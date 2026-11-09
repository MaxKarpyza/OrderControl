package ru.ordercontrol.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * Рабочее время: одно окно «с–до» для всех отмеченных дней недели.
 * Границы — в минутах от начала суток, конец не включается.
 */
data class WorkSchedule(
    val startMinute: Int = DEFAULT_START_MINUTE,
    val endMinute: Int = DEFAULT_END_MINUTE,
    val days: Set<DayOfWeek> = DEFAULT_DAYS,
) {

    fun isWorkingAt(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (days.isEmpty()) return false
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
        if (dateTime.dayOfWeek !in days) return false
        val minutes = dateTime.hour * 60 + dateTime.minute
        return if (startMinute <= endMinute) {
            minutes in startMinute until endMinute
        } else {
            // Окно через полночь: 22:00–06:00.
            minutes >= startMinute || minutes < endMinute
        }
    }

    companion object {
        const val DEFAULT_START_MINUTE = 8 * 60
        const val DEFAULT_END_MINUTE = 17 * 60

        val DEFAULT_DAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )

        fun minutesOf(hour: Int, minute: Int): Int = hour * 60 + minute

        fun hourOf(minuteOfDay: Int): Int = (minuteOfDay / 60) % 24

        fun minuteOf(minuteOfDay: Int): Int = minuteOfDay % 60
    }
}
