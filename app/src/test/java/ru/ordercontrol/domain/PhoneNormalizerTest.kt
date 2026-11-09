package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNormalizerTest {

    @Test
    fun `eight prefix becomes plus seven`() {
        assertEquals("+79991234567", PhoneNormalizer.normalize("89991234567"))
    }

    @Test
    fun `seven prefix becomes plus seven`() {
        assertEquals("+79991234567", PhoneNormalizer.normalize("79991234567"))
    }

    @Test
    fun `ten digits starting with nine get plus seven`() {
        assertEquals("+79991234567", PhoneNormalizer.normalize("9991234567"))
    }

    @Test
    fun `ten digit landline gets plus seven`() {
        assertEquals("+74951234567", PhoneNormalizer.normalize("4951234567"))
    }

    @Test
    fun `formatting is ignored`() {
        assertEquals("+79991234567", PhoneNormalizer.normalize("+7 (999) 123-45-67"))
    }

    @Test
    fun `international number keeps country code`() {
        assertEquals("+375291234567", PhoneNormalizer.normalize("+375 29 123-45-67"))
    }

    @Test
    fun `hidden number is null`() {
        assertNull(PhoneNormalizer.normalize(null))
        assertNull(PhoneNormalizer.normalize(""))
        assertNull(PhoneNormalizer.normalize("   "))
        assertNull(PhoneNormalizer.normalize("-1"))
        assertNull(PhoneNormalizer.normalize("-2"))
        assertNull(PhoneNormalizer.normalize("0"))
        assertNull(PhoneNormalizer.normalize("private"))
    }

    @Test
    fun `service number is kept without plus`() {
        assertEquals("112", PhoneNormalizer.normalize("112"))
    }

    @Test
    fun `same number in different forms is equal`() {
        val forms = listOf(
            "+7 999 123-45-67",
            "8 999 123 45 67",
            "79991234567",
            "9991234567",
        )
        val normalized = forms.map { PhoneNormalizer.normalize(it) }.distinct()
        assertEquals(listOf("+79991234567"), normalized)
    }

    @Test
    fun `letters and brackets are ignored`() {
        assertEquals("+79991234567", PhoneNormalizer.normalize("+7 (999) abc 12-34-567"))
    }

    @Test
    fun `eleven digits with other country code keep plus`() {
        assertEquals("+375291234567", PhoneNormalizer.normalize("375291234567"))
    }

    @Test
    fun `already normalized number stays the same`() {
        assertEquals("+79991234567", PhoneNormalizer.normalize("+79991234567"))
    }
}
