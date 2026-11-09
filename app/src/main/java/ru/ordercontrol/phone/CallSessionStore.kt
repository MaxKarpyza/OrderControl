package ru.ordercontrol.phone

import android.content.Context
import androidx.core.content.edit

/** Состояние одного текущего звонка. */
data class CallSession(
    val subscriptionId: Int,
    val number: String?,
    val startedAt: Long,
    val answered: Boolean,
)

/**
 * Состояние текущих звонков, переживающее пересоздание процесса ресивера.
 *
 * Сессии хранятся по `subscriptionId`, чтобы звонок на второй SIM не сбрасывал
 * состояние разговора на первой.
 */
class CallSessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Начало входящего звонка.
     *
     * Прошивки присылают `RINGING` по нескольку раз, причём номер приходит
     * не в каждом событии. Поэтому уже известный номер не затирается пустым,
     * а время начала остаётся самым ранним: по нему ищется запись в журнале.
     */
    fun start(subscriptionId: Int, number: String?, at: Long) {
        val existing = read(subscriptionId)
        // Второй входящий на ту же SIM (call waiting) не должен стирать
        // уже отвеченный разговор.
        if (existing != null && existing.answered) return

        val session = if (existing == null) {
            CallSession(subscriptionId, number, at, answered = false)
        } else {
            existing.copy(
                number = existing.number ?: number,
                startedAt = minOf(existing.startedAt, at),
            )
        }

        prefs.edit(commit = true) {
            putString(key(subscriptionId), encode(session))
        }
    }

    fun markAnswered(subscriptionId: Int) {
        val sessions = active()
        if (sessions.isEmpty()) return
        val target = if (subscriptionId >= 0) {
            sessions.firstOrNull { it.subscriptionId == subscriptionId }
        } else {
            sessions.maxByOrNull { it.startedAt }
        } ?: return
        if (target.answered) return

        prefs.edit(commit = true) {
            putString(key(target.subscriptionId), encode(target.copy(answered = true)))
        }
    }

    fun active(): List<CallSession> = prefs.all.keys
        .filter { it.startsWith(KEY_PREFIX) }
        .mapNotNull { key -> prefs.getString(key, null)?.let(::decode) }

    /**
     * Забирает и удаляет сессии указанной SIM; `-1` — все активные.
     *
     * Номер SIM в `RINGING` и `IDLE` приходит не всегда, поэтому если по
     * указанной SIM ничего нет, возвращаются все активные сессии: иначе
     * звонок потерялся бы из-за несовпадения идентификаторов.
     */
    fun finish(subscriptionId: Int): List<CallSession> {
        val all = active()
        val bySubscription = if (subscriptionId >= 0) {
            all.filter { it.subscriptionId == subscriptionId }
        } else {
            emptyList()
        }
        val target = bySubscription.ifEmpty { all }
        if (target.isEmpty()) return emptyList()

        prefs.edit(commit = true) {
            target.forEach { remove(key(it.subscriptionId)) }
        }
        return target
    }

    private fun read(subscriptionId: Int): CallSession? =
        prefs.getString(key(subscriptionId), null)?.let(::decode)

    private fun key(subscriptionId: Int): String = "$KEY_PREFIX$subscriptionId"

    private fun encode(session: CallSession): String = listOf(
        session.subscriptionId,
        session.number.orEmpty(),
        session.startedAt,
        if (session.answered) 1 else 0,
    ).joinToString(SEPARATOR)

    private fun decode(raw: String): CallSession? {
        val parts = raw.split(SEPARATOR)
        if (parts.size != 4) return null
        val subscriptionId = parts[0].toIntOrNull() ?: return null
        val startedAt = parts[2].toLongOrNull() ?: return null
        return CallSession(
            subscriptionId = subscriptionId,
            number = parts[1].takeIf { it.isNotEmpty() },
            startedAt = startedAt,
            answered = parts[3] == "1",
        )
    }

    private companion object {
        const val PREFS_NAME = "call_sessions"
        const val KEY_PREFIX = "session_"
        const val SEPARATOR = "\u001F"
    }
}
