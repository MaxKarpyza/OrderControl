package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Сопоставление на реальной выгрузке вкладки `27.10.26`
 * (`gviz/tq?tqx=out:csv`), первые строки таблицы.
 */
class SheetRealDataTest {

    private lateinit var rows: List<SheetOrderRow>

    @Before
    fun loadRows() {
        val stream = javaClass.getResourceAsStream("/sheet_27_10_26.csv")
        assertNotNull("нет ресурса sheet_27_10_26.csv", stream)
        val csv = SheetCsv.parse(stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
        rows = SheetAddresses.rows(csv)
    }

    private fun record(id: Long, address: String, bottles: String = "", at: Long = id) =
        MatchRecord(id = id, address = address, bottlesText = bottles, incomingAt = at)

    private fun match(records: List<MatchRecord>) =
        SheetOrderMatcher.match(records, rows, emptyMap(), emptySet(), "27.10.26")

    @Test
    fun `eight addresses parsed from real tab`() {
        assertEquals(8, rows.size)
        val byAddress = rows.associate { it.address to it.bottles }
        assertEquals(2, byAddress["Малахова 1-62"])
        assertEquals(1, byAddress["Козыря 23-6"])
        assertEquals(1, byAddress["Артема 114 2-314"])
        assertEquals(2, byAddress["Артема 114 1-204"])
        assertEquals(1, byAddress["Артёма 114 1-304"])
        assertEquals(2, byAddress["Челюскинцев 212а-18"])
        assertEquals(1, byAddress["Водоканал 212 елки"])
        assertEquals(2, byAddress["Артема 116а-115"])
        assertNull(byAddress[""])
    }

    @Test
    fun `real addresses match their rows exactly`() {
        val result = match(
            listOf(
                record(1, "Малахова 1-62"),
                record(2, "Артема 114 2-314"),
                record(3, "Артёма 114 1-304"),
                record(4, "Челюскинцев 212а-18"),
                record(5, "Артема 116а-115"),
            ),
        )
        val byRecord = result.exact.associate { it.recordId to it.row }
        assertEquals(5, result.exact.size)
        assertEquals(2, byRecord.getValue(1).bottles)
        assertEquals(1, byRecord.getValue(2).bottles)
        assertEquals(1, byRecord.getValue(3).bottles)
        assertEquals(2, byRecord.getValue(4).bottles)
        assertEquals(2, byRecord.getValue(5).bottles)
        assertEquals(
            listOf("Артема 116а-115", "Челюскинцев 212а-18", "Артёма 114 1-304", "Артема 114 2-314", "Малахова 1-62"),
            result.exact.map { it.row.address },
        )
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun `operator written address with yo and comma matches exactly`() {
        val result = match(listOf(record(1, "Артёма 114, кв. 1-304", bottles = "1 бутыль")))
        val exact = result.exact.single()
        assertEquals(1L, exact.recordId)
        assertEquals("Артёма 114 1-304", exact.row.address)
        assertFalse(exact.bottlesDiffer)
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun `address without flat is ambiguous but closes nothing`() {
        val result = match(listOf(record(1, "Артема 114")))
        assertTrue(result.exact.isEmpty())
        val candidate = result.ambiguous.single()
        assertEquals(1L, candidate.recordId)
        assertTrue(candidate.partial)
        assertTrue(
            candidate.row.address in listOf("Артема 114 2-314", "Артема 114 1-204", "Артёма 114 1-304"),
        )
    }

    @Test
    fun `ambiguous picks similar row and never shares it`() {
        val result = match(
            listOf(
                record(1, "Артема 114", at = 100),
                record(2, "Артема 114", at = 200),
            ),
        )
        assertTrue(result.exact.isEmpty())
        assertEquals(2, result.ambiguous.size)
        val rowsUsed = result.ambiguous.map { it.row.rowIndex }
        assertEquals(rowsUsed.size, rowsUsed.distinct().size)
        assertTrue(result.ambiguous.all { it.partial })
    }

    @Test
    fun `vodokanal prefix is a candidate but neighbour house is not`() {
        assertTrue(SheetOrderMatcher.isPartialCandidate("Водоканал 212", "Водоканал 212 елки"))

        val withPrefix = match(listOf(record(1, "Водоканал 212")))
        assertEquals("Водоканал 212 елки", withPrefix.ambiguous.single().row.address)

        val withNeighbour = match(listOf(record(1, "Водоканал 213")))
        assertTrue(withNeighbour.exact.isEmpty())
        assertTrue(withNeighbour.ambiguous.isEmpty())
        assertFalse(SheetOrderMatcher.isPartialCandidate("Водоканал 213", "Водоканал 212 елки"))
        assertTrue(SheetOrderMatcher.similarity("Водоканал 213", "Водоканал 212 елки") < 0.80)
    }

    @Test
    fun `unfinished word is the same place`() {
        assertTrue(
            SheetOrderMatcher.isPartialCandidate("Куйб налог 407", "Куйб налоговая 407"),
        )
        assertTrue(
            SheetOrderMatcher.isPartialCandidate("Куйб налоговая 407", "Куйб налог 407"),
        )
        assertFalse(
            SheetOrderMatcher.isPartialCandidate("Куйб налог 407", "Куйб налоговая 409"),
        )
    }

    @Test
    fun `one letter word is not a prefix of another`() {
        // «5» и «53» — разные дома, даже если одно слово начинается с другого.
        assertFalse(SheetOrderMatcher.isPartialCandidate("Кирова 5", "Кирова 53"))
        assertFalse(SheetOrderMatcher.isPartialCandidate("Кирова 5", "Кирова 501"))
    }
}
