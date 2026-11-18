package ru.ordercontrol.ui.order

import org.junit.Assert.assertEquals
import org.junit.Test

class DistrictOptionsTest {

    @Test
    fun `пустые значения и повторы отбрасываются`() {
        val districts = setOf(" Центр ", "", "Александровка", "Центр", "   ")

        assertEquals(listOf("Александровка", "Центр"), districtOptions(districts))
    }

    @Test
    fun `порядок алфавитный`() {
        val districts = setOf("Центр", "Александровка", "Мирный")

        assertEquals(
            listOf("Александровка", "Мирный", "Центр"),
            districtOptions(districts),
        )
    }

    @Test
    fun `частые районы идут первыми`() {
        val districts = setOf("Александровка", "Мирный", "Центр")

        assertEquals(
            listOf("Центр", "Мирный", "Александровка"),
            districtOptions(districts, mapOf("Центр" to 5, "Мирный" to 2)),
        )
    }

    @Test
    fun `при равной частоте порядок алфавитный`() {
        val districts = setOf("Центр", "Александровка", "Мирный")

        assertEquals(
            listOf("Александровка", "Мирный", "Центр"),
            districtOptions(
                districts,
                mapOf("Центр" to 3, "Александровка" to 3, "Мирный" to 3),
            ),
        )
    }

    @Test
    fun `пустой набор даёт пустой список`() {
        assertEquals(emptyList<String>(), districtOptions(emptySet()))
    }
}
