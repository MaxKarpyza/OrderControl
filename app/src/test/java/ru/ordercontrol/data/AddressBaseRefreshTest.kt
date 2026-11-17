package ru.ordercontrol.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBaseRefreshTest {

    private val today = LocalDate.of(2026, 11, 17)

    private fun tab(date: LocalDate) = "%02d.%02d.%02d".format(
        date.dayOfMonth,
        date.monthValue,
        date.year % 100,
    )

    @Test
    fun `читаются вкладки за десять дней до сегодняшней и все новые`() {
        val names = listOf(
            tab(today.minusDays(11)),
            tab(today.minusDays(10)),
            tab(today.minusDays(3)),
            tab(today),
            tab(today.plusDays(1)),
        )

        val selected = selectTabsToRead(names, parsed = names.toSet(), today = today, full = false)

        assertEquals(
            listOf(tab(today.minusDays(10)), tab(today.minusDays(3)), tab(today), tab(today.plusDays(1))),
            selected,
        )
    }

    @Test
    fun `неразобранная вкладка читается даже из старой истории`() {
        val names = listOf(tab(today.minusDays(40)), tab(today))

        val selected = selectTabsToRead(
            names,
            parsed = setOf(tab(today)),
            today = today,
            full = false,
        )

        assertEquals(listOf(tab(today.minusDays(40)), tab(today)), selected)
    }

    @Test
    fun `первый разбор берёт всю историю`() {
        val names = listOf(tab(today.minusDays(40)), tab(today.minusDays(11)), tab(today))

        val selected = selectTabsToRead(names, parsed = emptySet(), today = today, full = true)

        assertEquals(names, selected)
    }

    @Test
    fun `служебные вкладки не читаются`() {
        val names = listOf("_tg_queue", tab(today))

        val selected = selectTabsToRead(names, parsed = emptySet(), today = today, full = false)

        assertEquals(listOf(tab(today)), selected)
    }

    @Test
    fun `обновление пора на десятое скачивание`() {
        assertFalse(isRefreshDue(fetchCount = 9, lastRefreshAt = 0L, now = 1L))
        assertTrue(isRefreshDue(fetchCount = 10, lastRefreshAt = 0L, now = 1L))
    }

    @Test
    fun `обновление пора раз в сутки даже без скачиваний`() {
        val day = 24 * 60 * 60 * 1000L

        assertFalse(isRefreshDue(fetchCount = 0, lastRefreshAt = 0L, now = day - 1))
        assertTrue(isRefreshDue(fetchCount = 0, lastRefreshAt = 0L, now = day))
    }
}
