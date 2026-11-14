package ru.ordercontrol.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ordercontrol.data.db.CallRecord

/** Текст, который уходит в колонку комментариев таблицы. */
class SheetCommentTest {

    private fun record(comment: String = "", withPhone: Boolean = false, phone: String? = "+79990000001") =
        CallRecord(
            phoneNormalized = phone,
            incomingAt = 0L,
            comment = comment,
            withPhoneInComment = withPhone,
        )

    @Test
    fun `пустой комментарий и телефон не нужен`() {
        assertEquals("", buildSheetComment(record()))
    }

    @Test
    fun `только текст оператора`() {
        assertEquals("позвонить после 12", buildSheetComment(record(comment = "позвонить после 12")))
    }

    @Test
    fun `телефон после текста через пробел`() {
        assertEquals(
            "позвонить после 12 тел +79990000001",
            buildSheetComment(record(comment = "позвонить после 12", withPhone = true)),
        )
    }

    @Test
    fun `без текста в комментарий уходит только телефон`() {
        assertEquals("тел +79990000001", buildSheetComment(record(withPhone = true)))
    }

    @Test
    fun `скрытый номер не дописывается`() {
        assertEquals("звони", buildSheetComment(record(comment = "звони", withPhone = true, phone = null)))
    }
}
