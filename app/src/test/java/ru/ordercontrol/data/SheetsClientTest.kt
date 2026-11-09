package ru.ordercontrol.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetsClientTest {

    private val client = SheetsClient()

    @Test
    fun `tab name is date in dd MM yy`() {
        assertEquals("27.10.26", SheetsClient.tabName(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun `tab name keeps leading zeros`() {
        assertEquals("05.03.27", SheetsClient.tabName(LocalDate.of(2027, 3, 5)))
    }

    @Test
    fun `tab name of tomorrow crosses month`() {
        val today = LocalDate.of(2026, 10, 31)
        assertEquals("01.11.26", SheetsClient.tabName(today.plusDays(1)))
    }

    @Test
    fun `csv url uses gid`() {
        assertEquals(
            "https://docs.google.com/spreadsheets/d/" +
                "1HDK5_dbN52XIE3XScBhVJtvtJvgbEMwH8U6Trozjmqg" +
                "/gviz/tq?tqx=out:csv&gid=178090042",
            SheetsClient.csvUrl("178090042"),
        )
    }

    @Test
    fun `csv url uses custom base`() {
        val custom = SheetsClient("https://example.com/sheet")
        assertEquals(
            "https://example.com/sheet/gviz/tq?tqx=out:csv&gid=42",
            custom.csvUrl("42"),
        )
    }

    @Test
    fun `html view url`() {
        assertEquals(SheetsClient.DEFAULT_BASE_URL + "/htmlview", SheetsClient.htmlViewUrl())
    }

    @Test
    fun `sheet index maps names to gids`() {
        val html = """
            items.push({name: "26.10.26", pageUrl: "https:\/\/docs.google.com\/spreadsheets\/d\/x\/htmlview\/sheet?headers\x3dtrue&gid=2045740287", gid: "2045740287",initialSheet: ("2045740287" == gid)});
            items.push({name: "27.10.26", pageUrl: "https:\/\/docs.google.com\/spreadsheets\/d\/x\/htmlview\/sheet?headers\x3dtrue&gid=178090042", gid: "178090042",initialSheet: ("178090042" == gid)});
            items.push({name: "28.10.26", pageUrl: "https:\/\/docs.google.com\/spreadsheets\/d\/x\/htmlview\/sheet?headers\x3dtrue&gid=1495953258", gid: "1495953258",initialSheet: ("1495953258" == gid)});
        """.trimIndent()

        val index = SheetsClient.parseSheetIndex(html)

        assertEquals("178090042", index["27.10.26"])
        assertEquals("1495953258", index["28.10.26"])
        assertEquals("2045740287", index["26.10.26"])
        assertNull(index["01.01.99"])
    }

    @Test
    fun `sheet index of unrelated html is empty`() {
        assertTrue(SheetsClient.parseSheetIndex("<html><body>нет таблицы</body></html>").isEmpty())
    }

    @Test
    fun `missing tab is not looked up in csv`() {
        val index = SheetsClient.parseSheetIndex("""{name: "27.10.26", gid: "1"}""")
        // Завтрашней вкладки нет — клиент обязан пропустить её, а не качать
        // первый лист под её именем.
        assertNull(index["28.10.26"])
    }

    @Test
    fun `edit page index maps escaped entries`() {
        // Фрагмент страницы редактирования: сначала gid, затем имя вкладки.
        val html = """[21350203,"[311,0,\"178090042\",[{\"1\":[[0,0,\"27.10.26\"],[{\"1\":6}]]}],1091,26]"]""" +
            """[21350203,"[312,0,\"1495953258\",[{\"1\":[[0,0,\"28.10.26\"],[{\"1\":6}]]}],1091,26]"]"""

        val index = SheetsClient.parseEditIndex(html)

        assertEquals("178090042", index["27.10.26"])
        assertEquals("1495953258", index["28.10.26"])
    }

    @Test
    fun `edit page without sheets gives empty index`() {
        assertTrue(SheetsClient.parseEditIndex("<html>нет</html>").isEmpty())
    }

    @Test
    fun `edit url`() {
        assertEquals(SheetsClient.DEFAULT_BASE_URL + "/edit", SheetsClient.editUrl())
    }
}
