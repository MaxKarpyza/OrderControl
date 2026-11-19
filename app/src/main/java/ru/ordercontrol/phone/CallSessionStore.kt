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
 * Минимальный шов хранения: логика [CallSessionStore] остаётся чистым Kotlin
 * и тестируется без Android. Значение `null` в [apply] означает удаление ключа.
 */
internal interface SessionStorage {

    /** Текущее содержимое хранилища: ключ → строка. */
    fun load(): Map<String, String>

    /** Атомарно применяет изменения. */
    fun apply(changed: Map<String, String?>)
}

/** Android-реализация [SessionStorage] поверх SharedPreferences. */
internal class PrefsSessionStorage(context: Context) : SessionStorage {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    override fun apply(changed: Map<String, String?>) {
        prefs.edit(commit = true) {
            changed.forEach { (key, value) ->
                if (value == null) remove(key) else putString(key, value)
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "call_sessions"
    }
}

/**
 * Состояние текущих звонков, переживающее пересоздание процесса ресивера.
 *
 * Сессии хранятся по `subscriptionId`, чтобы звонок на второй SIM не сбрасывал
 * состояние разговора на первой.
 *
 * @param now источник текущего времени, подменяется в тестах.
 */
internal class CallSessionStore(
    private val storage: SessionStorage,
    private val now: () -> Long = System::currentTimeMillis,
) {

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

        storage.apply(mapOf(key(subscriptionId) to encode(session)))
    }

    /**
     * Разговор начался.
     *
     * Прошивка присылает `subscription` не во всех событиях: если ни одна
     * сессия не совпала по номеру SIM, берём самую свежую — иначе отвеченный
     * входящий был бы помечен как исходящий и попал бы в «пропущенные».
     *
     * @return `true`, если был входящий звонок. `false` значит, что звонок
     *         исходящий: у входящего есть `RINGING`, у исходящего только
     *         `OFFHOOK`.
     */
    fun markAnswered(subscriptionId: Int): Boolean {
        val sessions = active()
        if (sessions.isEmpty()) return false
        val matched = if (subscriptionId >= 0) {
            sessions.firstOrNull { it.subscriptionId == subscriptionId }
        } else {
            null
        }
        val target = matched ?: sessions.maxByOrNull { it.startedAt } ?: return false
        if (!target.answered) {
            storage.apply(
                mapOf(key(target.subscriptionId) to encode(target.copy(answered = true)))
            )
        }
        return true
    }

    /**
     * Исходящий звонок: входящего сеанса нет, а трубку уже взяли.
     *
     * Отметка нужна, чтобы в `IDLE` отличить конец исходящего звонка от
     * конца входящего: по нему сверяется журнал и снимается отметка
     * «пропущен» у перезвонившего клиента.
     */
    fun markOutgoing() {
        storage.apply(mapOf(KEY_OUTGOING to OUTGOING_VALUE))
    }

    /**
     * Исходящий звонок закончился.
     *
     * @return `true`, если исходящий звонок действительно был.
     */
    fun clearOutgoing(): Boolean {
        if (storage.load()[KEY_OUTGOING] != OUTGOING_VALUE) return false
        storage.apply(mapOf(KEY_OUTGOING to null))
        return true
    }

    fun active(): List<CallSession> {
        val nowMs = now()
        val sessions = storage.load()
            .filterKeys { it.startsWith(KEY_PREFIX) }
            .mapNotNull { (key, raw) -> decode(raw)?.let { key to it } }
        // Ребут в разгар звонка оставляет фантомную сессию: старше
        // MAX_SESSION_MS она к текущему звонку отношения не имеет —
        // игнорируем и удаляем.
        val (live, expired) = sessions.partition { (_, session) ->
            nowMs - session.startedAt <= MAX_SESSION_MS
        }
        if (expired.isNotEmpty()) {
            storage.apply(expired.associate { (key, _) -> key to null })
        }
        return live.map { it.second }
    }

    /**
     * Забирает и удаляет сессии указанной SIM; `-1` — все активные.
     *
     * Номер SIM приходит не во всех событиях. Поэтому при `subscriptionId >= 0`
     * без точного совпадения фолбэк берёт только сессии с неизвестной SIM:
     * чужой живой звонок под другой известной SIM (call waiting на второй
     * SIM) финализировать нельзя.
     */
    fun finish(subscriptionId: Int): List<CallSession> {
        val all = active()
        val target = if (subscriptionId < 0) {
            all
        } else {
            all.filter { it.subscriptionId == subscriptionId }.ifEmpty {
                all.filter { it.subscriptionId == UNKNOWN_SUBSCRIPTION }
            }
        }
        if (target.isEmpty()) return emptyList()

        storage.apply(target.associate { key(it.subscriptionId) to null })
        return target
    }

    private fun read(subscriptionId: Int): CallSession? =
        storage.load()[key(subscriptionId)]?.let(::decode)
            // Устаревшую сессию считаем отсутствующей: новый звонок её затрёт.
            ?.takeIf { now() - it.startedAt <= MAX_SESSION_MS }

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

    internal companion object {
        const val KEY_PREFIX = "session_"
        const val KEY_OUTGOING = "outgoing_active"
        const val OUTGOING_VALUE = "1"
        const val SEPARATOR = "\u001F"

        /** Номер SIM не пришёл в extra `subscription`. */
        const val UNKNOWN_SUBSCRIPTION = -1

        /** Срок жизни сессии: фантом после ребута не переживает звонок. */
        const val MAX_SESSION_MS = 10 * 60 * 1000L
    }
}
