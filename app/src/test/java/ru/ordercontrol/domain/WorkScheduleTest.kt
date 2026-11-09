package ru.ordercontrol.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkScheduleTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val schedule = WorkSchedule(
        startMinute = WorkSchedule.minutesOf(8, 0),
        endMinute = WorkSchedule.minutesOf(17, 0),
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `inside working window`() {
        assertTrue(schedule.isWorkingAt(millis(2026, 10, 27, 8, 0), zone))
        assertTrue(schedule.isWorkingAt(millis(2026, 10, 27, 12, 30), zone))
        assertTrue(schedule.isWorkingAt(millis(2026, 10, 27, 16, 59), zone))
    }

    @Test
    fun `end boundary is exclusive`() {
        assertFalse(schedule.isWorkingAt(millis(2026, 10, 27, 17, 0), zone))
    }

    @Test
    fun `before opening is not working time`() {
        assertFalse(schedule.isWorkingAt(millis(2026, 10, 27, 7, 59), zone))
    }

    @Test
    fun `day off is not working time`() {
        // 2026-10-29 — четверг, не отмечен.
        assertFalse(schedule.isWorkingAt(millis(2026, 10, 29, 12, 0), zone))
    }

    @Test
    fun `empty days means never working`() {
        val empty = schedule.copy(days = emptySet())
        assertFalse(empty.isWorkingAt(millis(2026, 10, 27, 12, 0), zone))
    }

    @Test
    fun `overnight window wraps midnight`() {
        val night = WorkSchedule(
            startMinute = WorkSchedule.minutesOf(22, 0),
            endMinute = WorkSchedule.minutesOf(6, 0),
            days = setOf(DayOfWeek.MONDAY),
        )
        assertTrue(night.isWorkingAt(millis(2026, 10, 26, 23, 0), zone))
        assertTrue(night.isWorkingAt(millis(2026, 10, 26, 5, 59), zone))
        assertFalse(night.isWorkingAt(millis(2026, 10, 26, 6, 0), zone))
        assertFalse(night.isWorkingAt(millis(2026, 10, 26, 12, 0), zone))
    }

    @Test
    fun `equal start and end means no working time`() {
        val zero = schedule.copy(
            startMinute = WorkSchedule.minutesOf(9, 0),
            endMinute = WorkSchedule.minutesOf(9, 0),
        )
        assertFalse(zero.isWorkingAt(millis(2026, 10, 27, 9, 0), zone))
        assertFalse(zero.isWorkingAt(millis(2026, 10, 27, 15, 0), zone))
    }
}
