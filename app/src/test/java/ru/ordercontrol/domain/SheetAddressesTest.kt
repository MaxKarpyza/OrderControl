package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetAddressesTest {

    private val sample =
        "2,Малахова 1-62,2,450,звони,Мирный,,,,,0,,,112,,1,Артема 114 2-314,1,250,тел,Вишневского"

    @Test
    fun `real row gives two addresses`() {
        val rows = SheetAddresses.rows(SheetCsv.parse(sample))
        assertEquals(2, rows.size)
        assertEquals("Малахова 1-62", rows[0].address)
        assertEquals(2, rows[0].bottles)
        assertEquals("Артема 114 2-314", rows[1].address)
        assertEquals(1, rows[1].bottles)
        assertTrue(rows.all { it.rowIndex == 1 })
    }

    @Test
    fun `addresses from different columns keep their column`() {
        val rows = SheetAddresses.rows(SheetCsv.parse(sample))

        assertEquals(SheetAddresses.ADDRESS_COLUMNS[0], rows[0].column)
        assertEquals(SheetAddresses.ADDRESS_COLUMNS[2], rows[1].column)
    }

    @Test
    fun `row index is one based`() {
        val rows = SheetAddresses.rows(SheetCsv.parse("x\n,y,1"))
        assertEquals(2, rows.single().rowIndex)
    }

    @Test
    fun `empty addresses are skipped`() {
        val rows = SheetAddresses.rows(SheetCsv.parse("a,,1,,,,,,,2"))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `blank address with spaces is skipped`() {
        val rows = SheetAddresses.rows(SheetCsv.parse("a,   ,1"))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `non numeric bottles give null`() {
        val rows = SheetAddresses.rows(SheetCsv.parse("a,Ленина 1,много"))
        assertEquals(1, rows.size)
        assertNull(rows.single().bottles)
    }

    @Test
    fun `missing bottles cell gives null`() {
        val rows = SheetAddresses.rows(SheetCsv.parse("a,Ленина 1"))
        assertNull(rows.single().bottles)
    }

    @Test
    fun `short row does not throw`() {
        val rows = SheetAddresses.rows(SheetCsv.parse(",2,3,4"))
        assertEquals(1, rows.size)
        assertEquals("2", rows.single().address)
        assertEquals(3, rows.single().bottles)

        val short = SheetAddresses.rows(SheetCsv.parse("1,2,3"))
        assertEquals("2", short.single().address)
        assertEquals(3, short.single().bottles)
    }

    @Test
    fun `price column is not used as bottles`() {
        val rows = SheetAddresses.rows(SheetCsv.parse("a,Ленина 1,,450"))
        assertNull(rows.single().bottles)
    }

    @Test
    fun `address is trimmed`() {
        val rows = SheetAddresses.rows(SheetCsv.parse("a,  Ленина 1  ,3"))
        assertEquals("Ленина 1", rows.single().address)
        assertEquals(3, rows.single().bottles)
    }

    @Test
    fun `address columns are B I Q`() {
        assertEquals(listOf(1, 8, 16), SheetAddresses.ADDRESS_COLUMNS)
    }
}
