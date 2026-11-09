package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressNormalizerTest {

    @Test
    fun `yo becomes e and case is lowered`() {
        assertEquals("артема", AddressNormalizer.normalize("АртЁма"))
    }

    @Test
    fun `punctuation becomes spaces`() {
        assertEquals(
            "артема 114 2 314",
            AddressNormalizer.normalize("Артёма 114, кв. 2-314"),
        )
    }

    @Test
    fun `apartment and office words are dropped`() {
        assertEquals(
            AddressNormalizer.normalize("Артема 114 1-304"),
            AddressNormalizer.normalize("Артёма 114, кв. 1-304"),
        )
        assertEquals(
            AddressNormalizer.normalize("Артема 84а"),
            AddressNormalizer.normalize("Артема 84а офис"),
        )
    }

    @Test
    fun `house number is not treated as noise`() {
        assertEquals("кирова 53 28", AddressNormalizer.normalize("Кирова 53-28"))
    }

    @Test
    fun `spaces are collapsed`() {
        assertEquals("ленина 1", AddressNormalizer.normalize("  Ленина   1  "))
    }

    @Test
    fun `empty text normalizes to empty`() {
        assertEquals("", AddressNormalizer.normalize("  ,. - "))
    }

    @Test
    fun `compact drops spaces and hyphens`() {
        assertEquals("артема1142314", AddressNormalizer.compact("Артема 114 2-314"))
        assertEquals("артема1142314", AddressNormalizer.compact("Артема 114-2-314"))
    }

    @Test
    fun `bottles from text`() {
        assertEquals(2, AddressNormalizer.bottlesFromText("2 по 19 л"))
        assertEquals(3, AddressNormalizer.bottlesFromText("3 бутыли"))
    }

    @Test
    fun `huge numbers are not bottles`() {
        assertNull(AddressNormalizer.bottlesFromText("залог 1200"))
        assertEquals(1, AddressNormalizer.bottlesFromText("залог 1200, 1 бутыль"))
    }

    @Test
    fun `empty bottles text gives null`() {
        assertNull(AddressNormalizer.bottlesFromText(""))
        assertNull(AddressNormalizer.bottlesFromText("без бутылей"))
    }

    @Test
    fun `zero is not bottles`() {
        assertNull(AddressNormalizer.bottlesFromText("0"))
    }
}
