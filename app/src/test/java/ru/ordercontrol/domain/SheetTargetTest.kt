package ru.ordercontrol.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ordercontrol.data.SheetsClient

/** Дата вкладки, в которую уйдёт заказ. */
class SheetTargetTest {

    private val zone: ZoneId = ZoneId.of("Europe/Kyiv")

    /** «Сегодня» для теста — пятница 20.11.2026. */
    private val today: LocalDate = LocalDate.of(2026, 11, 20)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 10): Long =
        LocalDate.of(year, month, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun date(day: DeliveryDay?, atMillis: Long, today: LocalDate = this.today): LocalDate =
        SheetTarget.date(
            incomingAt = atMillis,
            deliveryDay = day,
            zone = zone,
            today = today,
        )

    @Test
    fun `без дня доставки — текущая вкладка`() {
        // Звонок был неделю назад, но дату доставки не отмечали — пишем в текущую.
        val oldCall = at(2026, 11, 14)
        assertEquals(today, date(null, oldCall))
        assertEquals(today, date(DeliveryDay.OTHER_DAY, oldCall))
    }

    @Test
    fun `завтра и послезавтра считаются от дня звонка`() {
        // 12.11.2026 — четверг.
        val call = at(2026, 11, 12)
        assertEquals(LocalDate.of(2026, 11, 13), date(DeliveryDay.TOMORROW, call))
        assertEquals(LocalDate.of(2026, 11, 14), date(DeliveryDay.DAY_AFTER_TOMORROW, call))
    }

    @Test
    fun `понедельник — ближайший следующий`() {
        // 14.11.2026 — суббота, ближайший понедельник 16.11.
        assertEquals(LocalDate.of(2026, 11, 16), date(DeliveryDay.MONDAY, at(2026, 11, 14)))
        // Пятница 13.11 → понедельник 16.11.
        assertEquals(LocalDate.of(2026, 11, 16), date(DeliveryDay.MONDAY, at(2026, 11, 13)))
        // Сам понедельник 16.11 → следующий, 23.11.
        assertEquals(LocalDate.of(2026, 11, 23), date(DeliveryDay.MONDAY, at(2026, 11, 16)))
    }

    @Test
    fun `воскресенье переносится на понедельник`() {
        val sunday = LocalDate.of(2026, 11, 15)
        assertEquals(
            LocalDate.of(2026, 11, 16),
            date(null, at(2026, 11, 15), today = sunday),
        )
        // Суббота с пометкой «завтра» тоже даёт воскресенье → понедельник.
        assertEquals(
            LocalDate.of(2026, 11, 16),
            date(DeliveryDay.TOMORROW, at(2026, 11, 14)),
        )
    }

    @Test
    fun `имя вкладки — день и месяц с годом из двух цифр`() {
        assertEquals("16.11.26", SheetsClient.tabName(LocalDate.of(2026, 11, 16)))
        assertEquals("01.01.27", SheetsClient.tabName(LocalDate.of(2027, 1, 1)))
    }
}
