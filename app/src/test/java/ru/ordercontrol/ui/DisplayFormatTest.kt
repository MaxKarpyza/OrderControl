package ru.ordercontrol.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayFormatTest {

    @Test
    fun `russian number is split for reading`() {
        assertEquals("+7 (999) 123-45-67", DisplayFormat.phone("+79991234567"))
    }

    @Test
    fun `another country code is left as is`() {
        assertEquals("+995555123456", DisplayFormat.phone("+995555123456"))
    }

    @Test
    fun `short number is left as is`() {
        assertEquals("123456", DisplayFormat.phone("123456"))
    }
}
