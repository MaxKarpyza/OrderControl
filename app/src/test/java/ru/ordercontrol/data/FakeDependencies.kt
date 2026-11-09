package ru.ordercontrol.data

import java.time.DayOfWeek
import ru.ordercontrol.data.db.BlacklistEntry
import ru.ordercontrol.data.db.BlacklistDao
import ru.ordercontrol.domain.BlacklistKind
import ru.ordercontrol.domain.WorkSchedule

/** Расписание, которое всегда считает время рабочим. */
class FakeScheduleProvider(
    var schedule: WorkSchedule = WorkSchedule(
        startMinute = 0,
        endMinute = 24 * 60 - 1,
        days = DayOfWeek.entries.toSet(),
    ),
) : ScheduleProvider {
    override suspend fun currentSchedule(): WorkSchedule = schedule
}

/** Телефонная книга в памяти: номер → имя. */
class FakeContactLookup(
    private val book: MutableMap<String, String> = mutableMapOf(),
    private val canCreate: Boolean = true,
) : ContactLookup {

    val created = mutableListOf<Pair<String, String>>()

    fun put(number: String, name: String) {
        book[number] = name
    }

    override suspend fun findName(number: String): String? = book[number]

    override suspend fun createContactIfAbsent(name: String, number: String): Boolean {
        if (!canCreate) return false
        if (book[number] != null) return false
        book[number] = name
        created += name to number
        return true
    }
}

/** Чёрный список в памяти. */
class FakeBlacklistDao : BlacklistDao {

    private val entries = mutableMapOf<String, BlacklistEntry>()

    fun all(): List<BlacklistEntry> = entries.values.toList()

    override suspend fun find(phone: String): BlacklistEntry? = entries[phone]

    override suspend fun put(entry: BlacklistEntry) {
        entries[entry.phoneNormalized] = entry
    }

    override suspend fun remove(phone: String) {
        entries.remove(phone)
    }
}
