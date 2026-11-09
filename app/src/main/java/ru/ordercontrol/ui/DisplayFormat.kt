package ru.ordercontrol.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import ru.ordercontrol.domain.WorkSchedule

object DisplayFormat {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
    private val fullFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    /** Сегодня — только время, иначе дата и время. */
    fun callTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return if (dateTime.toLocalDate() == LocalDate.now(zone)) {
            dateTime.format(timeFormatter)
        } else {
            dateTime.format(dateTimeFormatter)
        }
    }

    fun minuteOfDay(minuteOfDay: Int): String {
        val hour = WorkSchedule.hourOf(minuteOfDay)
        val minute = WorkSchedule.minuteOf(minuteOfDay)
        return "%02d:%02d".format(hour, minute)
    }

    /** Полная дата и время: для истории, где день важен. */
    fun fullDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return dateTime.format(fullFormatter)
    }

    /**
     * Номер для чтения вслух: `+79991234567` → `+7 (999) 123-45-67`.
     * Незнакомый формат остаётся как есть — лучше показать сырой номер,
     * чем потерять цифры.
     */
    fun phone(normalized: String): String {
        val digits = normalized.filter { it.isDigit() }
        return if (normalized.startsWith("+7") && digits.length == 11) {
            "+7 (${digits.substring(1, 4)}) " +
                "${digits.substring(4, 7)}-" +
                "${digits.substring(7, 9)}-" +
                digits.substring(9, 11)
        } else {
            normalized
        }
    }
}
