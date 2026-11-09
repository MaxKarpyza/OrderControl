package ru.ordercontrol.phone

import ru.ordercontrol.data.CallRepository
import ru.ordercontrol.domain.PhoneNormalizer

/**
 * Превращает завершившийся звонок в запись.
 *
 * Запись создаётся сразу и без ожидания. Журнал нужен только как подсказка
 * для номера: некоторые прошивки не отдают номер в `PHONE_STATE`. Ждать
 * журнал нельзя — ресиверу отведено около 10 секунд, а на холодном старте
 * это время уходит на инициализацию базы. Поэтому журнал опрашивается один
 * раз, а если он ещё не обновился, номер дозаполняется позже в [CallSync].
 */
class CallFinalizer(
    private val calls: CallRepository,
    private val callLog: CallLogSource,
) {

    /**
     * Создаёт запись о завершившемся звонке.
     *
     * Номер берётся из `PHONE_STATE`, а при его отсутствии — из журнала,
     * если журнал уже содержит этот звонок. В любом случае запись создаётся
     * сразу: оператор открывает форму через секунду после звонка.
     */
    suspend fun resolve(session: CallSession): Long? {
        val sessionNumber = PhoneNormalizer.normalize(session.number)
        if (sessionNumber != null) {
            return calls.registerIncoming(
                numberRaw = session.number,
                answered = session.answered,
                incomingAt = session.startedAt,
                callLogId = null,
            )
        }

        // Номер пустой. Пробуем журнал один раз — без ожидания.
        if (callLog.hasPermission()) {
            val entry = findEntry(session)?.takeIf { !calls.isCallLogIdKnown(it.id) }
            if (entry != null) {
                return calls.registerIncoming(
                    numberRaw = entry.number,
                    answered = entry.answered,
                    incomingAt = entry.date,
                    callLogId = entry.id,
                )
            }
        }

        // Журнал ещё не обновился: запись создаётся сразу, номер появится
        // при следующей сверке. Если номер так и не найдётся, запись
        // останется со пометкой «скрытый номер».
        return calls.registerIncoming(
            numberRaw = null,
            answered = session.answered,
            incomingAt = session.startedAt,
            callLogId = null,
        )
    }

    /**
     * Запись журнала, относящаяся к этому звонку.
     *
     * Если номер из `PHONE_STATE` известен, ищем по номеру. Если нет — берём
     * звонок, ближайший по времени к началу сессии: пустой номер в
     * `PHONE_STATE` обычно означает, что прошивка его не отдала, а не то,
     * что номер скрыт. Ближайший, а не самый свежий: в журнале могут быть
     * звонки с другим временем из-за перевода часов.
     */
    fun findEntry(session: CallSession): CallLogEntry? {
        val since = session.startedAt - CLOCK_SKEW_MS
        val until = session.startedAt + MATCH_WINDOW_MS
        val entries = callLog.latestIncomingSince(since).filter { it.date in since..until }
        if (entries.isEmpty()) return null

        val sessionNumber = PhoneNormalizer.normalize(session.number)
        if (sessionNumber != null) {
            return entries.firstOrNull { PhoneNormalizer.normalize(it.number) == sessionNumber }
        }
        return entries.minByOrNull { kotlin.math.abs(it.date - session.startedAt) }
    }

    private companion object {
        const val CLOCK_SKEW_MS = 5 * 1000L

        /** Насколько после начала звонка запись журнала ещё считается этой. */
        const val MATCH_WINDOW_MS = 60 * 1000L
    }
}
