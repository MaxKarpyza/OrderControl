package ru.ordercontrol.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ordercontrol.data.db.CallRecord

class CallRecordAddressTest {

    private fun record(address: String, name: String? = null) = CallRecord(
        id = 1,
        phoneNormalized = "+79991234567",
        displayName = name,
        incomingAt = 0,
        addressText = address,
    )

    @Test
    fun `own address wins`() {
        assertEquals("Мира 11-7", record("Мира 11-7", "Тринити").deliveryAddress)
    }

    @Test
    fun `empty address is filled with the contact`() {
        assertEquals("Тринити", record("", "Тринити").deliveryAddress)
    }

    @Test
    fun `blank address is filled with the contact`() {
        assertEquals("Тринити", record("   ", "Тринити").deliveryAddress)
    }

    @Test
    fun `without contact and address there is nothing to deliver to`() {
        assertEquals("", record("", null).deliveryAddress)
        assertEquals("", record("", "  ").deliveryAddress)
    }
}
