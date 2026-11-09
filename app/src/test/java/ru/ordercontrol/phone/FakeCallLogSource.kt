package ru.ordercontrol.phone

/** Журнал звонков в памяти для JVM-тестов. */
class FakeCallLogSource(
    private val entries: MutableList<CallLogEntry> = mutableListOf(),
    var permission: Boolean = true,
) : CallLogSource {

    private var nextId = 1L

    fun add(
        number: String?,
        date: Long,
        answered: Boolean = true,
    ): Long {
        val id = nextId++
        val type = if (answered) {
            android.provider.CallLog.Calls.INCOMING_TYPE
        } else {
            android.provider.CallLog.Calls.MISSED_TYPE
        }
        entries += CallLogEntry(id = id, number = number, date = date, type = type)
        return id
    }

    fun all(): List<CallLogEntry> = entries.toList()

    override fun hasPermission(): Boolean = permission

    override fun latestIncomingSince(sinceMillis: Long): List<CallLogEntry> =
        entries.filter { it.date >= sinceMillis }.sortedByDescending { it.date }

    override fun incomingBetween(fromMillis: Long, toMillis: Long): List<CallLogEntry> =
        entries
            .filter { it.date in fromMillis..toMillis }
            .sortedByDescending { it.date }

    override fun existingIds(ids: Collection<Long>): Set<Long>? {
        if (!permission) return null
        return ids.filter { id -> entries.any { it.id == id } }.toSet()
    }
}
