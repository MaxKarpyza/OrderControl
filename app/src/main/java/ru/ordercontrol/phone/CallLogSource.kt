package ru.ordercontrol.phone

/** Запись системного журнала звонков, нужная приложению. */
data class CallLogEntry(
    val id: Long,
    val number: String?,
    val date: Long,
    val type: Int,
) {
    /** Отвечен ли звонок: тип `INCOMING` означает, что разговор состоялся. */
    val answered: Boolean get() = type == android.provider.CallLog.Calls.INCOMING_TYPE
}

/**
 * Источник системного журнала. Интерфейс нужен, чтобы правила работы
 * с журналом проверялись тестами без Android.
 */
interface CallLogSource {

    fun hasPermission(): Boolean

    /** Входящие, пропущенные и сброшенные звонки не раньше [sinceMillis]. */
    fun latestIncomingSince(sinceMillis: Long): List<CallLogEntry>

    /** Входящие звонки в интервале `[fromMillis, toMillis]`. */
    fun incomingBetween(fromMillis: Long, toMillis: Long): List<CallLogEntry>

    /**
     * Какие из [ids] ещё есть в журнале.
     * `null` — нет разрешения, сверять нельзя.
     */
    fun existingIds(ids: Collection<Long>): Set<Long>?
}
