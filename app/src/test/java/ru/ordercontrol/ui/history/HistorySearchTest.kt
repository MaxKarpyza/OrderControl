package ru.ordercontrol.ui.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySearchTest {

    @Test
    fun `empty query matches every record`() {
        assertTrue(HistorySearch.matches("Иван", "Кирова 5", "+79991234567", ""))
        assertTrue(HistorySearch.matches(null, "", null, "   "))
    }

    @Test
    fun `contact name is found regardless of case`() {
        assertTrue(HistorySearch.matches("Иван Петров", "Кирова 5", null, "петров"))
    }

    @Test
    fun `address is found by its part`() {
        assertTrue(HistorySearch.matches("Иван", "Континент город женский", null, "город"))
    }

    @Test
    fun `phone is found by digits without formatting`() {
        assertTrue(HistorySearch.matches(null, "", "+79991234567", "999-123"))
        assertTrue(HistorySearch.matches(null, "", "+79991234567", "1234567"))
    }

    @Test
    fun `short digit query does not match every phone`() {
        assertFalse(HistorySearch.matches(null, "", "+79991234567", "99"))
    }

    @Test
    fun `unrelated query matches nothing`() {
        assertFalse(HistorySearch.matches("Иван", "Кирова 5", "+79991234567", "Сидоров"))
    }
}
