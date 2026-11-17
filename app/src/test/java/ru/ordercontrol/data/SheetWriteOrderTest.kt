package ru.ordercontrol.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.domain.DeliveryDay

/**
 * Что уходит в таблицу по заказу.
 *
 * Заказ, принятый по телефону, попадает в лист с тем же набором полей,
 * что и ручной: адрес, количество бутылей и район.
 */
class SheetWriteOrderTest {

    private fun phoneOrder(
        address: String = "Артема 114",
        bottles: String = "2",
        district: String? = null,
        withPhoneInComment: Boolean = false,
    ) = CallRecord(
        id = 1L,
        phoneNormalized = "+79991234567",
        incomingAt = 1_793_098_800_000L,
        bottlesText = bottles,
        addressText = address,
        isAnswered = true,
        withPhoneInComment = withPhoneInComment,
        district = district,
    )

    @Test
    fun `заказ по звонку уходит с адресом и бутылями`() = runTest {
        val order = phoneOrder().toSheetOrder { null }

        assertEquals("Артема 114", order.address)
        assertEquals("2", order.bottles)
    }

    @Test
    fun `район из записи уходит в таблицу`() = runTest {
        val order = phoneOrder(district = "Гладковка").toSheetOrder { "из базы" }

        assertEquals("Гладковка", order.district)
    }

    @Test
    fun `район берётся из базы адресов, если в записи его нет`() = runTest {
        val order = phoneOrder(district = null).toSheetOrder { "Вишневского" }

        assertEquals("Вишневского", order.district)
    }

    @Test
    fun `без района и без адреса в базе ячейка района остаётся пустой`() = runTest {
        val order = phoneOrder(district = null).toSheetOrder { null }

        assertEquals("", order.district)
    }

    @Test
    fun `телефон дописывается в комментарий по требованию оператора`() = runTest {
        val order = phoneOrder(withPhoneInComment = true).toSheetOrder { null }

        assertEquals("тел +79991234567", order.comment)
    }

    @Test
    fun `заказ на другой день помечается`() = runTest {
        val record = phoneOrder().copy(deliveryDay = DeliveryDay.OTHER_DAY)

        assertTrue(record.toSheetOrder { null }.otherDay)
        assertFalse(phoneOrder().toSheetOrder { null }.otherDay)
    }

    @Test
    fun `ручной заказ уходит так же`() = runTest {
        val manual = CallRecord(
            id = 2L,
            phoneNormalized = null,
            incomingAt = 1_793_098_800_000L,
            bottlesText = "3",
            addressText = "Кочубея 4",
            isAnswered = true,
            isManual = true,
            district = "Гладковка",
        )

        val order = manual.toSheetOrder { null }

        assertEquals("Кочубея 4", order.address)
        assertEquals("3", order.bottles)
        assertEquals("Гладковка", order.district)
    }
}
