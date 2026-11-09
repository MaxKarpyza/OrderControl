package ru.ordercontrol.phone

import android.content.Context
import ru.ordercontrol.AppGraph
import ru.ordercontrol.domain.PhoneNormalizer

/**
 * Догоняет системный журнал: привязывает `callLogId` к записям, созданным
 * сразу после звонка, дозаполняет номер, который прошивка не отдала
 * в `PHONE_STATE`, и закрывает записи, чьи звонки оператор удалил из истории.
 *
 * Вызывается перед показом списка и перед обновлением виджетов — то есть
 * тогда, когда процесс приложения всё равно живёт, и можно не торопиться.
 */
object CallSync {

    suspend fun sync(context: Context) {
        val reader = CallLogReader(context)
        if (!reader.hasPermission()) return

        val taken = AppGraph.calls.usedCallLogIds().toMutableSet()
        fillMissingNumbers(reader, taken)
        backfillCallLogIds(reader, taken)

        val refs = AppGraph.calls.openCallRefs()
        if (refs.isEmpty()) return
        val existing = reader.existingIds(refs.map { it.callLogId }) ?: return
        AppGraph.calls.reconcileWithCallLog(existing)
    }

    /**
     * Восстанавливает звонки, которые приложение не увидело: пока оно не
     * запущено, система не доставляет `PHONE_STATE` (пакет в состоянии
     * stopped после установки). Читаем журнал за последний час и создаём
     * записи о входящих, которых нет в базе.
     *
     * @return сколько записей добавлено.
     */
    suspend fun catchUp(context: Context, hours: Int = CATCH_UP_HOURS): Int {
        val reader = CallLogReader(context)
        if (!reader.hasPermission()) return 0

        val since = System.currentTimeMillis() - hours * 60L * 60L * 1000L
        val entries = reader.latestIncomingSince(since)
        if (entries.isEmpty()) return 0

        val known = AppGraph.calls.usedCallLogIds().toMutableSet()
        var added = 0
        // От старых к новым: так «раньше» и склейка видят правильный порядок.
        for (entry in entries.sortedBy { it.date }) {
            if (entry.id in known) continue

            val phone = PhoneNormalizer.normalize(entry.number)
            if (phone != null && AppGraph.calls.hasRecordNear(phone, entry.date, MATCH_WINDOW_MS)) {
                // Запись об этом звонке уже есть, просто без callLogId.
                known += entry.id
                continue
            }

            val id = AppGraph.calls.registerIncoming(
                numberRaw = entry.number,
                answered = entry.answered,
                incomingAt = entry.date,
                callLogId = entry.id,
            )
            if (id != null) added++
            known += entry.id
        }
        return added
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

    /** Насколько близко по времени искать уже существующую запись. */
    private const val MATCH_WINDOW_MS = 2 * 60 * 1000L

    /**
     * За сколько часов восстанавливать пропущенные звонки.
     *
     * Час — это свежие звонки, которые оператор ещё помнит. Большее окно
     * подтянуло бы уже обработанные заказы и засорило список.
     */
    private const val CATCH_UP_HOURS = 1
}
