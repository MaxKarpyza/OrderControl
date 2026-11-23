package ru.ordercontrol.phone

import kotlinx.coroutines.sync.withLock
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
     *
     * Тело выполняется под процесс-широким локом [CallWrites]: иначе
     * восстановление на холодном старте задваивало бы эту запись.
     */
    suspend fun resolve(session: CallSession): Long? = CallWrites.mutex.withLock {
        val sessionNumber = PhoneNormalizer.normalize(session.number)
        // К `IDLE` журнал обычно уже содержит звонок, и его `DURATION` —
        // длительность разговора. В сессии есть только начало звонка,
        // поэтому там учтено ещё и время дозвона: журнал точнее.
        val entry = if (callLog.hasPermission()) findEntry(session) else null
        val durationSec = entry
            // Без номера в сессии запись журнала уже выбрана по времени;
            // с номером — сверяем, чтобы не взять длительность чужого звонка.
            ?.takeIf {
                sessionNumber == null ||
                    PhoneNormalizer.normalize(it.number) == sessionNumber
            }
            ?.durationSec
            ?.takeIf { it > 0 }
            ?: session.durationSecAt(System.currentTimeMillis())

        if (sessionNumber != null) {
            return@withLock calls.registerIncoming(
                numberRaw = session.number,
                answered = session.answered,
                incomingAt = session.startedAt,
                callLogId = null,
                durationSec = durationSec,
            )
        }

        // Номер пустой. Пробуем журнал один раз — без ожидания.
        if (entry != null && !calls.isCallLogIdKnown(entry.id)) {
            return@withLock calls.registerIncoming(
                numberRaw = entry.number,
                answered = entry.answered,
                incomingAt = entry.date,
                callLogId = entry.id,
                durationSec = durationSec,
            )
        }

        // Журнал ещё не обновился: запись создаётся сразу, номер появится
        // при следующей сверке. Если номер так и не найдётся, запись
        // останется со пометкой «скрытый номер».
        calls.registerIncoming(
            numberRaw = null,
            answered = session.answered,
            incomingAt = session.startedAt,
            callLogId = null,
            durationSec = durationSec,
        )
    }

    /**
     * Запасная длительность из сессии: когда журнал ещё не обновился.
     *
     * Разговор считается от начала звонка — момента ответа прошивка не
     * сообщает, поэтому сюда попадает и время дозвона. У неотвеченного
     * звонка длительности нет: он не разговор.
     */
    private fun CallSession.durationSecAt(endedAt: Long): Int? =
        if (!answered) {
            null
        } else {
            ((endedAt - startedAt) / 1000).toInt().takeIf { it > 0 }
        }

    /**
     * Запись журнала для звонка с пустым номером в `PHONE_STATE`.
     *
     * Пустой номер обычно означает, что прошивка его не отдала, а не то,
     * что номер скрыт. Берём звонок, ближайший по времени к началу сессии,
     * а не самый свежий: в журнале могут быть звонки с другим временем
     * из-за перевода часов.
     */
    fun findEntry(session: CallSession): CallLogEntry? {
        val since = session.startedAt - CLOCK_SKEW_MS
        val until = session.startedAt + MATCH_WINDOW_MS
        return callLog.latestIncomingSince(since)
            .filter { it.date in since..until }
            .minByOrNull { kotlin.math.abs(it.date - session.startedAt) }
    }

    private companion object {
        const val CLOCK_SKEW_MS = 5 * 1000L

        /** Насколько после начала звонка запись журнала ещё считается этой. */
        const val MATCH_WINDOW_MS = 60 * 1000L
    }
}
