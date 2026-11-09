package ru.ordercontrol.domain

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryDayTest {

    @Test
    fun `monday to thursday offers tomorrow and day after tomorrow`() {
        val expected = listOf(
            DeliveryDay.TOMORROW,
            DeliveryDay.DAY_AFTER_TOMORROW,
            DeliveryDay.OTHER_DAY,
        )
        assertEquals(expected, DeliveryDay.options(DayOfWeek.MONDAY))
        assertEquals(expected, DeliveryDay.options(DayOfWeek.TUESDAY))
        assertEquals(expected, DeliveryDay.options(DayOfWeek.WEDNESDAY))
        assertEquals(expected, DeliveryDay.options(DayOfWeek.THURSDAY))
    }

    @Test
    fun `friday offers tomorrow and monday`() {
        assertEquals(
            listOf(DeliveryDay.TOMORROW, DeliveryDay.MONDAY, DeliveryDay.OTHER_DAY),
            DeliveryDay.options(DayOfWeek.FRIDAY),
        )
    }

    @Test
    fun `weekend offers monday only`() {
        val expected = listOf(DeliveryDay.MONDAY, DeliveryDay.OTHER_DAY)
        assertEquals(expected, DeliveryDay.options(DayOfWeek.SATURDAY))
        assertEquals(expected, DeliveryDay.options(DayOfWeek.SUNDAY))
    }
}
