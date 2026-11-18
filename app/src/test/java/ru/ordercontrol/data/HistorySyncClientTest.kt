package ru.ordercontrol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySyncClientTest {

    private val client = HistorySyncClient()

    @Test
    fun `разбирает чужие строки и подтверждённые свои`() {
        val json = """
            {"ok":true,"accepted":["опер_37-A3F:1:100"],"history":[
              {"id":"опер_37-A3F:1:100","author":"опер_37-A3F","phone":"+79990000001",
               "address":"Малахова 1-62","district":"Мирный","bottles":"2",
               "comment":"звони","processedAt":100},
              {"id":"опер_12-BCD:5:200","author":"опер_12-BCD","phone":"+79990000009",
               "address":"Козыря 21-77","district":"","bottles":"1",
               "comment":"","processedAt":200}
            ]}
        """.trimIndent()

        val result = client.parse(json) as HistorySyncResult.Success

        assertEquals(setOf("опер_37-A3F:1:100"), result.accepted)
        assertEquals(2, result.rows.size)

        val own = result.rows[0]
        assertEquals("опер_37-A3F:1:100", own.id)
        assertEquals("+79990000001", own.phone)
        assertEquals("Малахова 1-62", own.address)
        assertEquals("Мирный", own.district)
        assertEquals("2", own.bottles)
        assertEquals("звони", own.comment)
        assertEquals(100L, own.processedAt)

        // Пустой район приходит как null, а не пустой строкой.
        assertEquals(null, result.rows[1].district)
    }

    @Test
    fun `строки без номера пропускаются`() {
        val json = """{"ok":true,"history":[{"id":"a","phone":"","address":"x","processedAt":1}]}"""

        val result = client.parse(json) as HistorySyncResult.Success

        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun `старый скрипт с no_tab считается неподдерживаемым`() {
        val result = client.parse("""{"ok":false,"error":"no_tab"}""")

        assertEquals(HistorySyncResult.Unsupported, result)
    }

    @Test
    fun `ответ без поля history считается неподдерживаемым`() {
        val result = client.parse("""{"ok":true,"tab":"14.11.26","written":[]}""")

        assertEquals(HistorySyncResult.Unsupported, result)
    }

    @Test
    fun `битый ответ — ошибка`() {
        val result = client.parse("<html>ошибка</html>")

        assertTrue(result is HistorySyncResult.Failure)
    }

    @Test
    fun `в теле отправки есть все нужные поля`() {
        val fields = pushFields(
            HistoryPushItem(
                id = "опер_37-A3F:1:100",
                phone = "+79990000001",
                address = "Малахова 1-62",
                district = "Мирный",
                bottles = "2",
                comment = "звони",
                processedAt = 100,
            ),
        )

        assertEquals(
            listOf("id", "phone", "address", "district", "bottles", "comment", "processedAt"),
            fields.keys.toList(),
        )
        assertEquals("+79990000001", fields["phone"])
        assertEquals(100L, fields["processedAt"])
    }

    @Test
    fun `район без значения уходит пустой строкой`() {
        val fields = pushFields(
            HistoryPushItem(
                id = "a",
                phone = "+79990000001",
                address = "x",
                district = null,
                bottles = "",
                comment = "",
                processedAt = 1,
            ),
        )

        assertEquals("", fields["district"])
    }
}
