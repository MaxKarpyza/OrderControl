package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetCsvTest {

    @Test
    fun `simple rows are split by comma`() {
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("1", "2", "3")),
            SheetCsv.parse("a,b,c\n1,2,3"),
        )
    }

    @Test
    fun `quoted field keeps comma`() {
        assertEquals(
            listOf(listOf("Малахова 1-62, кв. 5", "2")),
            SheetCsv.parse("\"Малахова 1-62, кв. 5\",2"),
        )
    }

    @Test
    fun `escaped quotes are unescaped`() {
        assertEquals(
            listOf(listOf("он сказал \"да\"", "x")),
            SheetCsv.parse("\"он сказал \"\"да\"\"\",x"),
        )
    }

    @Test
    fun `newline inside quotes stays in field`() {
        assertEquals(
            listOf(listOf("a\nb", "c")),
            SheetCsv.parse("\"a\nb\",c"),
        )
    }

    @Test
    fun `crlf is a single row separator`() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d")),
            SheetCsv.parse("a,b\r\nc,d\r\n"),
        )
    }

    @Test
    fun `empty text gives empty list`() {
        assertEquals(emptyList<List<String>>(), SheetCsv.parse(""))
    }

    @Test
    fun `unclosed quote does not throw`() {
        assertEquals(
            listOf(listOf("a", "b, c")),
            SheetCsv.parse("a,\"b, c"),
        )
    }

    @Test
    fun `rows are not padded to the same width`() {
        val rows = SheetCsv.parse("a,b,c\n1,2")
        assertEquals(3, rows[0].size)
        assertEquals(2, rows[1].size)
    }

    @Test
    fun `empty trailing field is kept`() {
        assertEquals(listOf(listOf("a", "")), SheetCsv.parse("a,"))
    }
}
