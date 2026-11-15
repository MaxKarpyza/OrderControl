package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetAddressBaseTest {

    /** Новая раскладка: адрес, бутыли, объём, комментарий, район. */
    private val sample =
        "1,Козыря 21-77,1,100,,Широкий,,,Лапа,,0,,,127,,1,Овражная 29,3,750,,Куйбышевский"

    @Test
    fun `three blocks give three addresses`() {
        val blocks = SheetAddressBase.parse(SheetCsv.parse(sample))
        assertEquals(3, blocks.size)
        assertEquals("Козыря 21-77", blocks[0].rows.single().address)
        assertEquals("Лапа", blocks[1].rows.single().address)
        assertEquals("Овражная 29", blocks[2].rows.single().address)
    }

    @Test
    fun `bottles come from the cell next to the address`() {
        val blocks = SheetAddressBase.parse(SheetCsv.parse(sample))
        assertEquals(1, blocks[0].rows.single().bottles)
        assertEquals(3, blocks[2].rows.single().bottles)
    }

    @Test
    fun `candidates keep every column where a district may be`() {
        val blocks = SheetAddressBase.parse(SheetCsv.parse(sample))
        val candidates = blocks[0].rows.single().candidates
        assertEquals("Широкий", candidates[5])
    }

    @Test
    fun `old layout keeps the district in the fourth column`() {
        // Старая раскладка: район в колонке E, комментарий в F.
        val blocks = SheetAddressBase.parse(SheetCsv.parse("1,Козыря 49-14,1,250,Широкий,После 12"))
        val candidates = blocks[0].rows.single().candidates
        assertEquals("Широкий", candidates[4])
        assertEquals("После 12", candidates[5])
    }

    @Test
    fun `district in the sixth column is kept as a candidate`() {
        // Район иногда пишут через колонку: комментарий, пусто, район.
        val blocks = SheetAddressBase.parse(
            SheetCsv.parse("1,Ленинский 130,1,250,тел +79990000000,,Мирный")
        )
        val candidates = blocks[0].rows.single().candidates
        assertEquals("Мирный", candidates[6])
    }

    @Test
    fun `empty addresses are skipped`() {
        val blocks = SheetAddressBase.parse(SheetCsv.parse("a,,1"))
        assertTrue(blocks.all { it.rows.isEmpty() })
    }

    @Test
    fun `zero bottles mean no order`() {
        val blocks = SheetAddressBase.parse(SheetCsv.parse(",Ленина 1,0,550"))
        assertNull(blocks[0].rows.single().bottles)
    }

    @Test
    fun `address is trimmed`() {
        val blocks = SheetAddressBase.parse(SheetCsv.parse(",  Ленина 1  ,2"))
        assertEquals("Ленина 1", blocks[0].rows.single().address)
    }

    @Test
    fun `empty tab gives no rows`() {
        val blocks = SheetAddressBase.parse(SheetCsv.parse(""))
        assertTrue(blocks.all { it.rows.isEmpty() })
    }
}
