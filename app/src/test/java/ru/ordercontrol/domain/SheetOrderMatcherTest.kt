package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetOrderMatcherTest {

    private fun row(index: Int, address: String, bottles: Int? = null, column: Int = 0) =
        SheetOrderRow(rowIndex = index, address = address, bottles = bottles, column = column)

    private fun record(id: Long, address: String, bottles: String = "", at: Long = id) =
        MatchRecord(id = id, address = address, bottlesText = bottles, incomingAt = at)

    private fun match(
        records: List<MatchRecord>,
        rows: List<SheetOrderRow>,
        aliases: Map<String, String> = emptyMap(),
        used: Set<String> = emptySet(),
    ) = SheetOrderMatcher.match(records, rows, aliases, used, "tab")

    @Test
    fun `exact match closes record`() {
        val result = match(
            records = listOf(record(1, "Артема 114 2-314")),
            rows = listOf(row(1, "Артема 114 2-314", 2)),
        )
        assertEquals(1, result.exact.size)
        assertEquals(1L, result.exact.single().recordId)
        assertEquals(1, result.exact.single().row.rowIndex)
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun `different bottles are flagged but still exact`() {
        val result = match(
            records = listOf(record(1, "Артема 114", bottles = "3 бутыли")),
            rows = listOf(row(1, "Артема 114", 2)),
        )
        assertEquals(1, result.exact.size)
        assertTrue(result.exact.single().bottlesDiffer)
    }

    @Test
    fun `same bottles is not flagged`() {
        val result = match(
            records = listOf(record(1, "Артема 114", bottles = "2 по 19 л")),
            rows = listOf(row(1, "Артема 114", 2)),
        )
        assertFalse(result.exact.single().bottlesDiffer)
    }

    @Test
    fun `unknown bottles in form is not flagged`() {
        val result = match(
            records = listOf(record(1, "Артема 114", bottles = "")),
            rows = listOf(row(1, "Артема 114", 2)),
        )
        assertFalse(result.exact.single().bottlesDiffer)
    }

    @Test
    fun `two records for one row use it once`() {
        val result = match(
            records = listOf(
                record(1, "Артема 114", at = 100),
                record(2, "Артема 114", at = 200),
            ),
            rows = listOf(row(1, "Артема 114")),
        )
        assertEquals(listOf(2L), result.exact.map { it.recordId })
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun `used row keys are excluded`() {
        val result = match(
            records = listOf(record(1, "Артема 114")),
            rows = listOf(row(1, "Артема 114")),
            used = setOf(SheetOrderMatcher.rowKey("tab", row(1, "Артема 114 2-314"))),
        )
        assertTrue(result.exact.isEmpty())
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun `alias turns fuzzy match into exact`() {
        val records = listOf(record(1, "Артема 114"))
        val rows = listOf(row(1, "Артема 114 2-314"))

        val withoutAlias = match(records, rows)
        assertTrue(withoutAlias.exact.isEmpty())
        val partial = withoutAlias.ambiguous.single()
        assertTrue(partial.partial)
        assertTrue(partial.similarity < SheetOrderMatcher.SIMILARITY_THRESHOLD)

        val withAlias = match(
            records = records,
            rows = rows,
            aliases = mapOf("артема 114" to "артема 114 2 314"),
        )
        assertEquals(1, withAlias.exact.size)
        assertTrue(withAlias.ambiguous.isEmpty())
    }

    @Test
    fun `short address inside a longer one is a candidate`() {
        // В таблице клиента пишут полнее, в телефоне — короче.
        assertTrue(
            SheetOrderMatcher.isPartialCandidate("Город женский", "Континент город женский"),
        )
        assertTrue(
            SheetOrderMatcher.isPartialCandidate("Континент город женский", "Город женский"),
        )
        assertFalse(SheetOrderMatcher.isPartialCandidate("Город мужской", "Континент город женский"))
        assertFalse(SheetOrderMatcher.isPartialCandidate("Город", "Континент город женский"))
    }

    @Test
    fun `short address inside a longer one is ambiguous but closes nothing`() {
        val result = match(
            records = listOf(record(1, "Город женский")),
            rows = listOf(row(1, "Континент город женский")),
        )
        assertTrue(result.exact.isEmpty())
        val candidate = result.ambiguous.single()
        assertTrue(candidate.partial)
        assertEquals("Континент город женский", candidate.row.address)
    }

    @Test
    fun `rejected pair is not offered again`() {
        val rows = listOf(row(1, "Континент город женский"))
        val records = listOf(record(1, "Город женский"))
        val rejected = setOf(
            SheetOrderMatcher.pairKey("Город женский", "Континент город женский"),
        )

        val result = SheetOrderMatcher.match(
            records = records,
            rows = rows,
            aliases = emptyMap(),
            usedRowKeys = emptySet(),
            tabName = "27.10.26",
            rejectedPairs = rejected,
        )

        assertTrue(result.exact.isEmpty())
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun `addresses in one row have different keys`() {
        val first = row(27, "Кирова 42-25", column = 1)
        val second = row(27, "Офицерский 69а-50", column = 16)

        assertEquals("27.10.26:27:1", SheetOrderMatcher.rowKey("27.10.26", first))
        assertEquals("27.10.26:27:16", SheetOrderMatcher.rowKey("27.10.26", second))
    }

    @Test
    fun `used address in one column does not block another column of the same row`() {
        val first = row(27, "Кирова 42-25", column = 1)
        val second = row(27, "Офицерский 69а-50", column = 16)

        val result = SheetOrderMatcher.match(
            records = listOf(record(1, "Офицерский 69а-50")),
            rows = listOf(first, second),
            aliases = emptyMap(),
            usedRowKeys = setOf(SheetOrderMatcher.rowKey("27.10.26", first)),
            tabName = "27.10.26",
        )

        assertEquals(1, result.exact.size)
        assertEquals("Офицерский 69а-50", result.exact.single().row.address)
    }

    @Test
    fun `partial candidate needs prefix with house number`() {        assertTrue(SheetOrderMatcher.isPartialCandidate("Артема 114", "Артема 114 2-314"))
        assertTrue(SheetOrderMatcher.isPartialCandidate("Артема 114 2-314", "Артема 114"))
        assertTrue(SheetOrderMatcher.isPartialCandidate("Мира 11-7", "Мира 11"))
        assertTrue(SheetOrderMatcher.isPartialCandidate("Кирова 53", "Кирова 53-28"))
        assertTrue(SheetOrderMatcher.isPartialCandidate("Водоканал 212", "Водоканал 212 елки"))
        assertFalse(SheetOrderMatcher.isPartialCandidate("Артема 114", "Артема 116а"))
        assertFalse(SheetOrderMatcher.isPartialCandidate("Мира", "Мира 11"))
        assertFalse(SheetOrderMatcher.isPartialCandidate("Кирова 5", "Кирова 53"))
        assertFalse(SheetOrderMatcher.isPartialCandidate("Артема 114", "Артема 114"))
    }

    @Test
    fun `shorter address without flat is ambiguous`() {
        val result = match(
            records = listOf(record(1, "Мира 11-7")),
            rows = listOf(row(1, "Мира 11")),
        )
        assertTrue(result.exact.isEmpty())
        val candidate = result.ambiguous.single()
        assertTrue(candidate.partial)
        assertEquals(1, candidate.row.rowIndex)
    }

    @Test
    fun `partial candidate prefers more similar row`() {
        val result = match(
            records = listOf(record(1, "Артема 114")),
            rows = listOf(
                row(1, "Артема 114 1-204"),
                row(2, "Артема 114 2-314"),
                row(3, "Артема 116а-115"),
            ),
        )
        assertEquals(1, result.ambiguous.size)
        // У «1-204» хвост короче, сходство выше — выбирается эта строка.
        assertEquals(1, result.ambiguous.single().row.rowIndex)
    }

    @Test
    fun `specific record takes the row that continues its address`() {
        val result = match(
            records = listOf(
                record(1, "Артема 114", at = 100),
                record(2, "Артема 114 2", at = 200),
            ),
            rows = listOf(
                row(1, "Артема 114 2-314"),
                row(2, "Артема 114 1-204"),
            ),
        )
        val byRecord = result.ambiguous.associate { it.recordId to it.row.rowIndex }
        assertEquals(2, byRecord.size)
        // Более точная запись забирает «свою» строку, вторая получает оставшуюся.
        assertEquals(1, byRecord.getValue(2))
        assertEquals(2, byRecord.getValue(1))
    }

    @Test
    fun `equal similarity keeps earlier row`() {
        val result = match(
            records = listOf(record(1, "Артема 114")),
            rows = listOf(
                row(1, "Артема 114 1-204"),
                row(2, "Артема 114 1-204"),
            ),
        )
        val candidate = result.ambiguous.single()
        assertEquals(1, candidate.row.rowIndex)
        assertEquals(
            SheetOrderMatcher.similarity("Артема 114", "Артема 114 1-204"),
            candidate.similarity,
            0.0,
        )
        assertTrue(candidate.partial)
    }

    @Test
    fun `threshold candidate wins over partial one`() {
        val result = match(
            records = listOf(record(1, "Артема 114 2-314")),
            rows = listOf(
                row(1, "Артема 114"),
                row(2, "Артема 114 2314"),
            ),
        )
        val candidate = result.ambiguous.single()
        assertFalse(candidate.partial)
        assertEquals(2, candidate.row.rowIndex)
        assertTrue(candidate.similarity >= SheetOrderMatcher.SIMILARITY_THRESHOLD)
    }

    @Test
    fun `similar address is ambiguous`() {
        val result = match(
            records = listOf(record(1, "Артема 114 2-314")),
            rows = listOf(row(1, "Артема 114 2314")),
        )
        val candidate = result.ambiguous.single()
        assertEquals(1L, candidate.recordId)
        assertTrue(candidate.similarity >= SheetOrderMatcher.SIMILARITY_THRESHOLD)
    }

    @Test
    fun `very different address is not ambiguous`() {
        val result = match(
            records = listOf(record(1, "Малахова 1-62")),
            rows = listOf(row(1, "Артема 114 2-314")),
        )
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun `blank record address is ignored`() {
        val result = match(
            records = listOf(record(1, "   ")),
            rows = listOf(row(1, "Артема 114")),
        )
        assertTrue(result.exact.isEmpty())
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun `similar address of a newer record does not steal the exact row`() {
        // Свежая запись с похожим адресом не должна занимать строку, по
        // которой у более старой записи есть точное совпадение.
        val result = match(
            records = listOf(
                record(1, "Артема 114 2-31", at = 200),
                record(2, "Артема 114 2-314", at = 100),
            ),
            rows = listOf(row(1, "Артема 114 2-314")),
        )
        assertEquals(listOf(2L), result.exact.map { it.recordId })
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun `exact match is found even after a similar row in the table`() {
        val result = match(
            records = listOf(record(1, "Артема 114 2-314")),
            rows = listOf(
                row(1, "Артема 114 2-31"),
                row(2, "Артема 114 1-204"),
                row(3, "Артема 114 2-314"),
            ),
        )
        assertEquals(1, result.exact.size)
        assertEquals(3, result.exact.single().row.rowIndex)
    }

    @Test
    fun `confirmed alias still matches the original spelling`() {
        val result = match(
            records = listOf(record(1, "Артема 114")),
            rows = listOf(row(1, "Артема 114")),
            aliases = mapOf("артема 114" to "артема 114 2 314"),
        )
        assertEquals(1, result.exact.size)
        assertEquals(1, result.exact.single().row.rowIndex)
    }

    @Test
    fun `similarity of identical addresses is one`() {
        assertEquals(1.0, SheetOrderMatcher.similarity("Артема 114", "артема, 114"), 0.0)
    }

    @Test
    fun `similarity with empty side is zero`() {
        assertEquals(0.0, SheetOrderMatcher.similarity("", ""), 0.0)
        assertEquals(0.0, SheetOrderMatcher.similarity("Артема 114", ""), 0.0)
    }

    @Test
    fun `row key format`() {
        assertEquals("Мирный:7:0", SheetOrderMatcher.rowKey("Мирный", row(7, "Мирный 1")))
    }
}
