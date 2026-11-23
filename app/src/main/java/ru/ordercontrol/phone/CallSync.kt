package ru.ordercontrol.phone

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ordercontrol.AppGraph
import ru.ordercontrol.data.CallRepository
import ru.ordercontrol.domain.CallbackCall
import ru.ordercontrol.domain.PhoneNormalizer

/**
 * Процесс-широкий лок на запись звонков в базу.
 *
 * Виджеты, восстановление на старте и финализатор работают параллельно и
 * читают одинаковые снапшоты базы: без лока один звонок журнала мог бы
 * привязаться к двум записям. Захваты не вкладываются: под этим локом
 * нельзя вызывать `CallSync.sync`, `CallSync.catchUp` и
 * `CallFinalizer.resolve`.
 */
internal object CallWrites {
    val mutex = Mutex()
}

/**
 * Догоняет системный журнал: привязывает `callLogId` к записям, созданным
 * сразу после звонка, дозаполняет номер, который прошивка не отдала
 * в `PHONE_STATE`, и закрывает записи, чьи звонки оператор удалил из истории.
 *
 * Вызывается перед показом списка и перед обновлением виджетов — то есть
 * тогда, когда процесс приложения всё равно живёт, и можно не торопиться.
 */
object CallSync {

    suspend fun sync(context: Context) = CallWrites.mutex.withLock {
        val reader = CallLogReader(context)
        if (!reader.hasPermission()) return@withLock

        val taken = AppGraph.calls.usedCallLogIds().toMutableSet()
        fillMissingNumbers(reader, taken)
        backfillCallLogIds(reader, taken)
        syncCallbacks(reader)

        val refs = AppGraph.calls.openCallRefs()
        if (refs.isEmpty()) return@withLock
        val existing = reader.existingIds(refs.map { it.callLogId }) ?: return@withLock
        AppGraph.calls.reconcileWithCallLog(existing)
    }

    /**
     * Снимает отметку «пропущен» с записей, по которым состоялся разговор.
     *
     * Оператор мог перезвонить клиенту: входящий звонок тогда остался
     * пропущенным, хотя клиент уже обслужен. Разговор считается состоявшимся,
     * если в журнале есть исходящий звонок с ненулевой длительностью и он
     * случился после пропущенного.
     *
     * @return сколько записей перестали быть пропущенными.
     */
    suspend fun syncCallbacks(reader: CallLogSource): Int {
        val pending = AppGraph.calls.openMissed()
        if (pending.isEmpty()) return 0

        // Журнал смотрим с момента самого раннего пропущенного, но не дальше
        // полутора месяцев: запись может висеть месяцами, а журнал столько
        // не хранит.
        val from = maxOf(
            pending.minOf { it.incomingAt },
            System.currentTimeMillis() - MAX_LOOKBACK_MS,
        )
        val entries = reader.outgoingBetween(from, Long.MAX_VALUE)
        if (entries.isEmpty()) return 0

        val callbacks = entries.mapNotNull { entry ->
            val phone = PhoneNormalizer.normalize(entry.number) ?: return@mapNotNull null
            CallbackCall(phone = phone, date = entry.date, durationSec = entry.durationSec)
        }
        return AppGraph.calls.markAnsweredByCallback(callbacks)
    }

    /**
     * Восстанавливает звонки, которые приложение не увидело: пока оно не
     * запущено, система не доставляет `PHONE_STATE` (пакет в состоянии
     * stopped после установки). Читаем журнал за последний час и создаём
     * записи о входящих, которых нет в базе.
     *
     * Записи последних [RECENT_CALL_MS] пропускаются: свежим звонком ещё
     * владеет живой путь ресивера, и дубль не нужен.
     *
     * @return сколько записей добавлено.
     */
    suspend fun catchUp(context: Context, hours: Int = CATCH_UP_HOURS): Int =
        catchUp(CallLogReader(context), hours)

    /**
     * Журнал и репозиторий — отдельными параметрами, чтобы логика
     * проверялась тестами без Android (продовый граф им недоступен).
     */
    internal suspend fun catchUp(
        reader: CallLogSource,
        hours: Int = CATCH_UP_HOURS,
        calls: CallRepository = AppGraph.calls,
    ): Int {
        if (!reader.hasPermission()) return 0
        return CallWrites.mutex.withLock {
            val now = System.currentTimeMillis()
            val since = now - hours * 60L * 60L * 1000L
            val entries = reader.latestIncomingSince(since)
            if (entries.isEmpty()) return@withLock 0

            val known = calls.usedCallLogIds().toMutableSet()
            var added = 0
            // От старых к новым: так «раньше» и склейка видят правильный порядок.
            for (entry in entries.sortedBy { it.date }) {
                // Свежей записью владеет ресивер: его финализатор прямо
                // сейчас создаёт запись по этому звонку.
                if (entry.date > now - RECENT_CALL_MS) continue
                if (entry.id in known) continue

                val phone = PhoneNormalizer.normalize(entry.number)
                if (phone != null && calls.hasRecordNear(phone, entry.date, MATCH_WINDOW_MS)) {
                    // Запись об этом звонке уже есть, просто без callLogId.
                    known += entry.id
                    continue
                }

                val id = calls.registerIncoming(
                    numberRaw = entry.number,
                    answered = entry.answered,
                    incomingAt = entry.date,
                    callLogId = entry.id,
                    durationSec = entry.durationSec,
                )
                if (id != null) added++
                known += entry.id
            }
            added
        }
    }

    /**
     * Номер не попал в запись вовремя (журнал отстал сильнее, чем ждали).
     * Если в журнале есть звонок в том же окне — подставляем номер и имя.
     */
    private suspend fun fillMissingNumbers(reader: CallLogSource, taken: MutableSet<Long>) {
        val pending = AppGraph.calls.openWithoutNumber()
        if (pending.isEmpty()) return

        for (record in pending) {
            val entry = findEntry(reader, record.incomingAt, taken) ?: continue
            val phone = PhoneNormalizer.normalize(entry.number) ?: continue

            AppGraph.calls.fillNumber(
                recordId = record.id,
                phone = phone,
                callLogId = entry.id,
            )
            taken += entry.id
        }
    }

    /** Привязывает журнал к записям, созданным без него. */
    private suspend fun backfillCallLogIds(reader: CallLogSource, taken: MutableSet<Long>) {
        val pending = AppGraph.calls.openWithoutCallLogId()
        if (pending.isEmpty()) return

        for (record in pending) {
            val phone = record.phoneNormalized ?: continue
            val entry = findEntry(reader, record.incomingAt, taken) ?: continue
            if (PhoneNormalizer.normalize(entry.number) != phone) continue

            AppGraph.calls.attachCallLogId(recordId = record.id, callLogId = entry.id)
            taken += entry.id
        }
    }

    /** Звонок журнала, ближайший по времени к [atMillis] и ещё не занятый. */
    private fun findEntry(
        reader: CallLogSource,
        atMillis: Long,
        taken: Set<Long>,
    ): CallLogEntry? = reader
        .incomingBetween(atMillis - WINDOW_MS, atMillis + WINDOW_MS)
        .filter { it.id !in taken }
        .minByOrNull { kotlin.math.abs(it.date - atMillis) }

    private const val WINDOW_MS = 5 * 60 * 1000L

    /** Как далеко назад ищем исходящие звонки. */
    private const val MAX_LOOKBACK_MS = 45L * 24 * 60 * 60 * 1000L

    /** Насколько близко по времени искать уже существующую запись. */
    private const val MATCH_WINDOW_MS = 2 * 60 * 1000L

    /** Записи журнала новее не трогаем: ими ещё владеет живой путь ресивера. */
    private const val RECENT_CALL_MS = 2 * 60 * 1000L

    /**
     * За сколько часов восстанавливать пропущенные звонки.
     *
     * Час — это свежие звонки, которые оператор ещё помнит. Большее окно
     * подтянуло бы уже обработанные заказы и засорило список.
     */
    private const val CATCH_UP_HOURS = 1
}
