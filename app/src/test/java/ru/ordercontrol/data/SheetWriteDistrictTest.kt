package ru.ordercontrol.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ordercontrol.data.db.CallRecord

class SheetWriteDistrictTest {

    private fun record(
        address: String = "Кочубея 4",
        district: String? = null,
    ) = CallRecord(
        id = 1L,
        phoneNormalized = null,
        incomingAt = 0L,
        addressText = address,
        district = district,
    )

    @Test
    fun `район из записи важнее подстановки из базы`() = runTest {
        val district = resolveDistrict(record(district = "Гладковка")) { "Вишневского" }

        assertEquals("Гладковка", district)
    }

    @Test
    fun `без района в записи берём из базы адресов`() = runTest {
        val district = resolveDistrict(record()) { "Вишневского" }

        assertEquals("Вишневского", district)
    }

    @Test
    fun `неизвестный адрес даёт пустой район`() = runTest {
        val district = resolveDistrict(record()) { null }

        assertEquals("", district)
    }

    @Test
    fun `пустая строка в базе считается отсутствием района`() = runTest {
        val district = resolveDistrict(record()) { "  " }

        assertEquals("", district)
    }

    @Test
    fun `состав запроса содержит район`() {
        val fields = orderFields(
            SheetWriteOrder(
                address = "Кочубея 4",
                bottles = "3",
                comment = "",
                district = "Гладковка",
            ),
        )

        assertEquals("Кочубея 4", fields["address"])
        assertEquals("Гладковка", fields["district"])
    }

    @Test
    fun `без района в запросе пустая строка`() {
        val fields = orderFields(
            SheetWriteOrder(
                address = "Кочубея 4",
                bottles = "3",
                comment = "",
            ),
        )

        assertEquals("", fields["district"])
    }
}
