package ru.ordercontrol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.ordercontrol.data.db.AddressBaseEntry
import ru.ordercontrol.domain.SheetAddressEntry

class AddressBaseMergeTest {

    private val old = AddressBaseEntry(
        key = "ленина 1",
        address = "Ленина 1",
        district = "Центр",
        bottles = 2,
        updatedAt = 100L,
    )

    @Test
    fun `new district overwrites the old one`() {
        val merged = mergeAddressEntry(
            old,
            SheetAddressEntry(address = "Ленина 1", bottles = 3, district = "Кировский"),
            now = 200L,
        )
        assertEquals("Кировский", merged.district)
        assertEquals(3, merged.bottles)
    }

    @Test
    fun `empty district keeps the old one`() {
        val merged = mergeAddressEntry(
            old,
            SheetAddressEntry(address = "Ленина 1", bottles = 3, district = null),
            now = 200L,
        )
        assertEquals("Центр", merged.district)
    }

    @Test
    fun `empty bottles keep the old count`() {
        val merged = mergeAddressEntry(
            old,
            SheetAddressEntry(address = "Ленина 1", bottles = null, district = null),
            now = 200L,
        )
        assertEquals(2, merged.bottles)
    }

    @Test
    fun `key is normalized`() {
        val merged = mergeAddressEntry(
            null,
            SheetAddressEntry(address = "  Ленина 1, кв. 2 ", bottles = 1, district = null),
            now = 200L,
        )
        assertEquals("ленина 1 2", merged.key)
        assertEquals("Ленина 1, кв. 2", merged.address)
        assertNull(merged.district)
    }

    @Test
    fun `new address has no district`() {
        val merged = mergeAddressEntry(
            null,
            SheetAddressEntry(address = "Мира 5", bottles = 1, district = null),
            now = 200L,
        )
        assertNull(merged.district)
        assertEquals(200L, merged.updatedAt)
    }
}
