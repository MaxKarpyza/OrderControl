package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBaseVocabularyTest {

    /** Вкладка: адрес, бутыли, объём, комментарий, район. */
    private val newLayout = "1,Ленина 1,2,550,звонить после 12,Центр"
    private val oldLayout = "1,Ленина 1,2,550,Центр,звонить после 12"

    /**
     * Комментарии меняются от вкладки к вкладке: район в опоре пишут
     * одинаково, а комментарии — нет. С одинаковыми комментариями колонки
     * неразличимы при равенстве счётчиков.
     */
    private val comments = listOf("звонить после 12", "своя тара", "залог 100")

    private fun tabs(layout: String): List<List<SheetAddressBlock>> =
        comments.map { SheetAddressBase.parse(SheetCsv.parse(layout.replace("звонить после 12", it))) }

    @Test
    fun `bootstrap finds the district column of the fresh layout`() {
        val vocabulary = AddressBaseVocabulary()
        vocabulary.bootstrap(tabs(newLayout))

        val blocks = SheetAddressBase.parse(SheetCsv.parse(newLayout))
        val column = vocabulary.districtColumn(blocks[0], SheetAddressBase.BLOCKS[0].districtColumns)
        assertEquals(5, column)
    }

    @Test
    fun `bootstrap finds the district column of the old layout`() {
        val vocabulary = AddressBaseVocabulary()
        vocabulary.bootstrap(tabs(oldLayout))

        val blocks = SheetAddressBase.parse(SheetCsv.parse(oldLayout))
        val column = vocabulary.districtColumn(blocks[0], SheetAddressBase.BLOCKS[0].districtColumns)
        assertEquals(4, column)
    }

    @Test
    fun `known districts are remembered`() {
        val vocabulary = AddressBaseVocabulary(known = setOf("Центр"))
        assertTrue("Центр" in vocabulary.districts())
    }

    @Test
    fun `without a dictionary the column is not guessed`() {
        val vocabulary = AddressBaseVocabulary()
        val blocks = SheetAddressBase.parse(SheetCsv.parse(newLayout))
        assertNull(vocabulary.districtColumn(blocks[0], SheetAddressBase.BLOCKS[0].districtColumns))
    }

    @Test
    fun `district with digits gets learned`() {
        val vocabulary = AddressBaseVocabulary()
        // «5 мкр» — легитимный район с цифрой: раньше выкидывался вместе с ценами.
        val tabs = comments.map {
            SheetAddressBase.parse(SheetCsv.parse("1,Ленина 1,2,550,$it,5 мкр"))
        }
        vocabulary.bootstrap(tabs)
        assertTrue("5 мкр" in vocabulary.districts())
    }
}
