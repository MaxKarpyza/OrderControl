package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBaseVocabularyTest {

    /** Вкладка: адрес, бутыли, объём, комментарий, район. */
    private val newLayout = "1,Ленина 1,2,550,звонить после 12,Центр"
    private val oldLayout = "1,Ленина 1,2,550,Центр,звонить после 12"

    @Test
    fun `bootstrap finds the district column of the fresh layout`() {
        val vocabulary = AddressBaseVocabulary()
        val tabs = List(AddressBaseVocabulary.MIN_TABS) {
            SheetAddressBase.parse(SheetCsv.parse(newLayout))
        }
        vocabulary.bootstrap(tabs)

        val blocks = SheetAddressBase.parse(SheetCsv.parse(newLayout))
        val column = vocabulary.districtColumn(blocks[0], SheetAddressBase.BLOCKS[0].districtColumns)
        assertEquals(5, column)
    }

    @Test
    fun `bootstrap finds the district column of the old layout`() {
        val vocabulary = AddressBaseVocabulary()
        val tabs = List(AddressBaseVocabulary.MIN_TABS) {
            SheetAddressBase.parse(SheetCsv.parse(oldLayout))
        }
        vocabulary.bootstrap(tabs)

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
}
