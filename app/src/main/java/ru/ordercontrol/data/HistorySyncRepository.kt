package ru.ordercontrol.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.data.db.CallRecordDao
import ru.ordercontrol.data.db.SharedHistoryDao
import ru.ordercontrol.data.db.SharedHistoryEntry
import ru.ordercontrol.domain.CloseReason

/** Что получилось из обмена общей историей. */
data class HistorySyncOutcome(
    /** Сколько своих записей таблица приняла. */
    val pushed: Int,
    /** Сколько чужих записей пришло. */
    val received: Int,
    /** Причина ошибки; `null` — всё прошло. */
    val error: String? = null,
    /** На развёртывании скрипт без общей истории. */
    val outdatedScript: Boolean = false,
)

/**
 * Синхронизация истории обработанных заказов между операторами.
 *
 * Своя история остаётся в [CallRecord]: сюда попадают только чужие строки.
 * Один обмен отправляет новые свои записи, забирает чужие и заодно даёт
 * скрипту повод удалить строки старше срока хранения.
 *
 * Срок хранения продублирован здесь и в скрипте: записи старше него слать
 * бессмысленно — во вкладке их уже нет.
 */
class HistorySyncRepository(
    private val callDao: CallRecordDao,
    private val sharedDao: SharedHistoryDao,
    private val client: HistorySyncer,
    private val operators: OperatorProvider,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Два обмена одновременно не нужны: они сделали бы то же самое. */
    private val mutex = Mutex()

    suspend fun sync(): HistorySyncOutcome = mutex.withLock {
        val syncedAt = now()
        val since = syncedAt - RETENTION_MS
        val operator = operators.operator()

        val pending = callDao.unsentToHistory(reasons = CloseReason.HISTORY_REASONS, since = since)
        val byPushedId = pending.associateBy { record ->
            pushId(operator.id, record.id, record.processedAt ?: record.incomingAt)
        }
        val push = byPushedId.map { (id, record) -> record.toPushItem(id) }

        when (val result = client.sync(operator = operator.displayName, push = push)) {
            is HistorySyncResult.Success -> {
                // Таблица подтвердила: эти строки больше не нужно отправлять.
                val accepted = result.accepted.mapNotNull { byPushedId[it]?.id }
                if (accepted.isNotEmpty()) {
                    // Отметка одна на всю пачку: время обмена, а не каждой записи.
                    callDao.markHistorySent(ids = accepted, at = syncedAt)
                }

                // Свои строки из таблицы не показываем: по ним есть своя история.
                val others = result.rows.filterNot { it.id.startsWith("${operator.id}:") }
                sharedDao.replaceAll(others.map { it.toEntry(syncedAt) })

                HistorySyncOutcome(pushed = accepted.size, received = others.size)
            }

            HistorySyncResult.Unsupported -> HistorySyncOutcome(
                pushed = 0,
                received = 0,
                outdatedScript = true,
            )

            HistorySyncResult.NoNetwork -> HistorySyncOutcome(
                pushed = 0,
                received = 0,
                error = "нет сети",
            )

            is HistorySyncResult.Failure -> HistorySyncOutcome(
                pushed = 0,
                received = 0,
                error = result.reason,
            )
        }
    }

    companion object {
        /** Срок хранения строки во вкладке `history`; столько же в скрипте. */
        const val RETENTION_DAYS = 2

        val RETENTION_MS: Long = RETENTION_DAYS * 24 * 60 * 60 * 1000L

        /**
         * Идентификатор строки в таблице.
         *
         * Начинается с идентификатора оператора: по этому префиксу свои
         * строки отличаются от чужих, даже если оператор потом ввёл ФИО.
         * Время в ключе нужно на случай, если номер записи в базе повторно
         * используется после очистки.
         */
        internal fun pushId(operatorId: String, recordId: Long, processedAt: Long): String =
            "$operatorId:$recordId:$processedAt"
    }
}

private fun CallRecord.toPushItem(id: String): HistoryPushItem = HistoryPushItem(
    id = id,
    // Запрос не берёт записи без номера, но на всякий случай.
    phone = phoneNormalized.orEmpty(),
    address = deliveryAddress.trim(),
    district = district?.trim()?.takeIf { it.isNotEmpty() },
    bottles = bottlesText.trim(),
    comment = comment.trim(),
    processedAt = processedAt ?: incomingAt,
)

private fun SharedHistoryRow.toEntry(syncedAt: Long): SharedHistoryEntry = SharedHistoryEntry(
    remoteId = id,
    author = author.ifEmpty { "?" },
    phone = phone,
    address = address,
    district = district,
    bottles = bottles,
    comment = comment,
    processedAt = processedAt,
    syncedAt = syncedAt,
)
