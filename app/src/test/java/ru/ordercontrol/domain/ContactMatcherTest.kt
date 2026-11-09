package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMatcherTest {

    private val contacts = listOf(
        ContactEntry(name = "Иван", number = "+7 999 123-45-67"),
        ContactEntry(name = "Пётр", number = "8 (495) 123-45-67"),
        ContactEntry(name = "Без номера", number = null),
        ContactEntry(name = "  ", number = "89991112233"),
    )

    @Test
    fun `name is found for the same number in another form`() {
        assertEquals("Иван", ContactMatcher.findName("89991234567", contacts.asSequence()))
        assertEquals("Иван", ContactMatcher.findName("9991234567", contacts.asSequence()))
        assertEquals("Иван", ContactMatcher.findName("+79991234567", contacts.asSequence()))
    }

    @Test
    fun `landline number is matched`() {
        assertEquals("Пётр", ContactMatcher.findName("84951234567", contacts.asSequence()))
    }

    @Test
    fun `unknown number has no name`() {
        assertNull(ContactMatcher.findName("+79001112233", contacts.asSequence()))
    }

    @Test
    fun `hidden number has no name`() {
        assertNull(ContactMatcher.findName(null, contacts.asSequence()))
        assertNull(ContactMatcher.findName("", contacts.asSequence()))
        assertNull(ContactMatcher.findName("-1", contacts.asSequence()))
    }

    @Test
    fun `blank name in the book is not a match`() {
        assertNull(ContactMatcher.findName("+79991112233", contacts.asSequence()))
    }

    @Test
    fun `contact is created only when name is given and contact is absent`() {
        assertTrue(ContactMatcher.shouldCreate("Иван", existingName = null))
        assertFalse(ContactMatcher.shouldCreate("Иван", existingName = "Иван"))
        assertFalse(ContactMatcher.shouldCreate(null, existingName = null))
        assertFalse(ContactMatcher.shouldCreate("   ", existingName = null))
    }

    @Test
    fun `empty book gives no name`() {
        assertNull(ContactMatcher.findName("+79991234567", emptySequence()))
    }
}
