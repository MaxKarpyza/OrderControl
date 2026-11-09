package ru.ordercontrol.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.domain.CloseReason
import ru.ordercontrol.domain.SheetOrderMatcher
import ru.ordercontrol.domain.SheetOrderRow

class SheetCheckRepositoryTest {

    private val TODAY = "27.10.26"
    private val TOMORROW = "28.10.26"

    private val callDao = FakeCallRecordDao()
    private val aliasDao = FakeAddressAliasDao()
    private val repository = SheetCheckRepository(
        callDao = callDao,
        aliasDao = aliasDao,
        sheets = SheetsClient(),
    )

    private fun record(
        id: Long,
        address: String,
        bottles: String = "",
        at: Long = id,
    ) = CallRecord(
        id = id,
        phoneNormalized = "+7999000000$id",
        incomingAt = at,
        bottlesText = bottles,
        addressText = address,
    )

    private fun tab(vararg rows: SheetOrderRow) = SheetTab(name = TODAY, rows = rows.toList())

    private fun tab(name: String, vararg rows: SheetOrderRow) =
        SheetTab(name = name, rows = rows.toList())

    private fun row(index: Int, address: String, bottles: Int? = null, column: Int = 0) =
        SheetOrderRow(rowIndex = index, address = address, bottles = bottles, column = column)

    @Test
    fun `exact match closes record with sheet reason and key`() = runTest {
        callDao.add(record(1, "Артема 114 2-314"))

        val outcome = repository.apply(listOf(tab(row(3, "Артема 114 2-314", 2))))

        assertEquals(1, outcome.closedCount)
        assertEquals(listOf(TODAY), outcome.tabsChecked)
        assertEquals(1, outcome.rowsRead)
        assertEquals(1, outcome.recordsWithAddress)
        val closed = callDao.byId(1)!!
        assertTrue(closed.isProcessed)
        assertEquals(CloseReason.SHEET, closed.closeReason)
        assertEquals(SheetOrderMatcher.rowKey(TODAY, row(3, "Артема 114 2-314", 2)), closed.sheetMatchKey)
        assertTrue(closed.processedAt != null)
    }

    @Test
    fun `second apply does not close another record by same row`() = runTest {
        callDao.add(record(1, "Артема 114", at = 100))
        val rows = listOf(tab(row(5, "Артема 114")))

        val first = repository.apply(rows)
        assertEquals(1, first.closedCount)

        callDao.add(record(2, "Артема 114", at = 200))
        val second = repository.apply(rows)
        assertEquals(0, second.closedCount)
        assertTrue(!callDao.byId(2)!!.isProcessed)

        val third = repository.apply(rows)
        assertEquals(0, third.closedCount)
    }

    @Test
    fun `similar address does not block the exact match of another record`() = runTest {
        callDao.add(record(1, "Артема 114 2-31", at = 200))
        callDao.add(record(2, "Артема 114 2-314", at = 100))

        val outcome = repository.apply(listOf(tab(row(1, "Артема 114 2-314"))))

        assertEquals(1, outcome.closedCount)
        assertTrue(callDao.byId(2)!!.isProcessed)
        assertFalse(callDao.byId(1)!!.isProcessed)
        assertTrue(outcome.ambiguous.isEmpty())
    }

    @Test
    fun `ambiguous match does not close record and is reported`() = runTest {
        callDao.add(record(1, "Артема 114 2-314"))

        val outcome = repository.apply(listOf(tab(row(1, "Артема 114 2314"))))

        assertEquals(0, outcome.closedCount)
        assertEquals(1, outcome.ambiguous.size)
        assertEquals(1L, outcome.ambiguous.single().recordId)
        assertTrue(!callDao.byId(1)!!.isProcessed)
        assertNull(callDao.byId(1)!!.sheetMatchKey)
    }

    @Test
    fun `confirmed alias closes address as exact match`() = runTest {
        callDao.add(record(1, "Аретама 114 2-314"))

        val rows = listOf(tab(row(1, "Артема 114 2-314")))
        val before = repository.apply(rows)
        assertEquals(0, before.closedCount)
        assertEquals(1, before.ambiguous.size)

        repository.confirmAlias(
            aliasAddress = before.ambiguous.single().recordAddress,
            canonicalAddress = before.ambiguous.single().row.address,
        )

        val after = repository.apply(rows)
        assertEquals(1, after.closedCount)
        val closed = callDao.byId(1)!!
        assertEquals(CloseReason.SHEET, closed.closeReason)
        assertEquals(SheetOrderMatcher.rowKey(TODAY, row(1, "Артема 114", 1)), closed.sheetMatchKey)
    }

    @Test
    fun `alias is stored normalized`() = runTest {
        repository.confirmAlias("  Артёма 114 ", "АРТЕМА 114, 2-314")

        val stored = aliasDao.find("артема 114")
        assertEquals("артема 114 2 314", stored?.canonicalNormalized)
        val key = aliasDao.all().single().aliasNormalized
        assertEquals("артема 114", key)
        assertEquals("артема 114 2 314", aliasDao.find(key)?.canonicalNormalized)
    }

    @Test
    fun `bottle mismatch is reported but record is closed`() = runTest {
        callDao.add(record(1, "Артема 114", bottles = "3 бутыли"))

        val outcome = repository.apply(listOf(tab(row(1, "Артема 114", 2))))

        assertEquals(1, outcome.closedCount)
        assertEquals(1, outcome.exactWithBottleMismatch.size)
        assertEquals(1L, outcome.exactWithBottleMismatch.single().recordId)
        assertEquals(CloseReason.SHEET, callDao.byId(1)!!.closeReason)
    }

    @Test
    fun `matching bottles are not reported`() = runTest {
        callDao.add(record(1, "Артема 114", bottles = "2 по 19 л"))

        val outcome = repository.apply(listOf(tab(row(1, "Артема 114", 2))))

        assertEquals(1, outcome.closedCount)
        assertTrue(outcome.exactWithBottleMismatch.isEmpty())
    }

    @Test
    fun `record closed in one tab does not consume a row in the next tab`() = runTest {
        // Два звонка с одним адресом и одна и та же строка в обеих вкладках.
        callDao.add(record(1, "Артема 114 2-314", at = 100))
        callDao.add(record(2, "Артема 114 2-314", at = 200))

        val outcome = repository.apply(
            listOf(
                tab(TODAY, row(1, "Артема 114 2-314")),
                tab(TOMORROW, row(1, "Артема 114 2-314")),
            ),
        )

        assertEquals(2, outcome.closedCount)
        assertTrue(callDao.byId(1)!!.isProcessed)
        assertTrue(callDao.byId(2)!!.isProcessed)
    }

    @Test
    fun `rejected address pair is not offered on the next check`() = runTest {
        callDao.add(record(1, "Город женский", at = 100))
        val rows = listOf(tab(row(1, "Континент город женский")))

        val first = repository.apply(rows)
        assertEquals(1, first.ambiguous.size)
        assertEquals(0, first.closedCount)

        repository.rejectAlias("Город женский", "Континент город женский")

        val second = repository.apply(rows)
        assertTrue(second.ambiguous.isEmpty())
        assertEquals(0, second.closedCount)
        assertTrue(!callDao.byId(1)!!.isProcessed)
    }

    @Test
    fun `rejected pair does not block confirming another pair`() = runTest {
        callDao.add(record(1, "Город женский", at = 100))
        val rows = listOf(tab(row(1, "Континент город женский")))

        repository.rejectAlias("Город женский", "Континент город мужской")
        val outcome = repository.apply(rows)

        assertEquals(1, outcome.ambiguous.size)
    }

    @Test
    fun `second address in the same row is still matched after the first is closed`() = runTest {
        // В строке 27 листа два адреса: B и Q. Закрытие первого не должно
        // блокировать второй.
        callDao.add(record(1, "Кирова 42-25", at = 100))
        callDao.add(record(2, "Офицерский 69а-50", at = 200))
        val rows = listOf(
            tab(
                row(27, "Кирова 42-25", 2, column = 1),
                row(27, "Офицерский 69а-50", 2, column = 16),
            ),
        )

        val outcome = repository.apply(rows)

        assertEquals(2, outcome.closedCount)
        assertEquals(SheetOrderMatcher.rowKey(TODAY, row(27, "Кирова 42-25", 2, column = 1)), callDao.byId(1)!!.sheetMatchKey)
        assertEquals(SheetOrderMatcher.rowKey(TODAY, row(27, "Офицерский 69а-50", 2, column = 16)), callDao.byId(2)!!.sheetMatchKey)
    }

    @Test
    fun `contact name is used as delivery address when operator entered none`() = runTest {
        // Оператор не заполнил адрес: доставка идёт по контакту.
        callDao.add(record(1, "", at = 100).copy(displayName = "Тринити"))

        val outcome = repository.apply(listOf(tab(row(1, "Тринити", 2))))

        assertEquals(1, outcome.closedCount)
        assertEquals(1, outcome.recordsWithAddress)
        assertTrue(callDao.byId(1)!!.isProcessed)
    }

    @Test
    fun `own address is preferred over contact name`() = runTest {
        callDao.add(
            record(1, "Мира 11-7", at = 100).copy(displayName = "Тринити"),
        )

        val outcome = repository.apply(
            listOf(tab(row(1, "Тринити"), row(2, "Мира 11-7"))),
        )

        assertEquals(1, outcome.closedCount)
        assertEquals(SheetOrderMatcher.rowKey(TODAY, row(2, "Мира 11-7")), callDao.byId(1)!!.sheetMatchKey)
    }

    @Test
    fun `record without address is ignored`() = runTest {
        callDao.add(record(1, ""))

        val outcome = repository.apply(listOf(tab(row(1, "Артема 114"))))

        assertEquals(0, outcome.closedCount)
        assertEquals(0, outcome.recordsWithAddress)
        assertTrue(outcome.ambiguous.isEmpty())
        assertTrue(!callDao.byId(1)!!.isProcessed)
    }

    @Test
    fun `already processed record is not closed again`() = runTest {
        callDao.add(record(1, "Артема 114"))
        callDao.closeAll(at = 5, reason = CloseReason.DONE)

        val outcome = repository.apply(listOf(tab(row(1, "Артема 114"))))

        assertEquals(0, outcome.closedCount)
        assertEquals(CloseReason.DONE, callDao.byId(1)!!.closeReason)
        assertNull(callDao.byId(1)!!.sheetMatchKey)
    }
}
