package ru.ordercontrol.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.domain.DeliveryDay
import ru.ordercontrol.domain.SheetOrderMatcher
import ru.ordercontrol.domain.SheetOrderRow

/**
 * Отправка как целое: сеть подменяется швами `writeTab` и `fetchTab`,
 * поэтому подтверждение и защита от дублей проверяются без сервера.
 */
class SheetWriteRepositoryTest {

    private val callDao = FakeCallRecordDao()

    private fun record(id: Long, address: String, at: Long = id, day: DeliveryDay? = null) =
        CallRecord(
            id = id,
            phoneNormalized = "+7999000000$id",
            incomingAt = at,
            bottlesText = "2",
            addressText = address,
            isAnswered = true,
            deliveryDay = day,
        )

    private fun repository(
        writeTab: suspend (String, List<SheetWriteOrder>) -> SheetWriteResult,
        fetchTab: suspend (String) -> SheetTab? = { null },
    ) = SheetWriteRepository(
        callDao = callDao,
        client = SheetWriteClient(),
        sheets = SheetsClient(),
        addressBase = null,
        history = null,
        writeTab = writeTab,
        fetchTab = fetchTab,
    )

    @Test
    fun `NoNetwork в середине подтверждает уже записанные вкладки`() = runTest {
        // Первый заказ — текущая вкладка, второй — завтрашняя: запись обрывается
        // на второй.
        callDao.add(record(1, "Артема 114", at = 200))
        callDao.add(record(2, "Кирова 5", at = 100, day = DeliveryDay.TOMORROW))

        var calls = 0
        var writtenTab: String? = null
        val outcome = repository(
            writeTab = { tab, orders ->
                calls += 1
                if (calls == 1) {
                    writtenTab = tab
                    SheetWriteResult.Success(tab, orders.map { SheetWrittenRow(it.address, 1) })
                } else {
                    SheetWriteResult.NoNetwork
                }
            },
            fetchTab = { tab ->
                SheetTab(tab, listOf(SheetOrderRow(rowIndex = 1, address = "Артема 114", bottles = 2, column = 1)))
            },
        ).send { }

        assertEquals(2, calls)
        assertTrue(outcome.errors.contains("нет сети"))
        // Первая вкладка записана — её заказ подтверждён, а не оставлен открытым.
        assertEquals(1, outcome.confirmed)
        val closed = callDao.byId(1)!!
        assertTrue(closed.isProcessed)
        assertNotNull(closed.sheetWrittenAt)
        assertEquals(SheetOrderMatcher.rowKey(writtenTab!!, 1, 1), closed.sheetMatchKey)
        assertFalse(callDao.byId(2)!!.isProcessed)
    }

    @Test
    fun `ключ из подтверждения блокирует проверку той же строки`() = runTest {
        callDao.add(record(1, "Артема 114", at = 200))

        var writtenTab: String? = null
        repository(
            writeTab = { tab, orders ->
                writtenTab = tab
                SheetWriteResult.Success(tab, orders.map { SheetWrittenRow(it.address, 1) })
            },
            fetchTab = { tab ->
                SheetTab(tab, listOf(SheetOrderRow(rowIndex = 1, address = "Артема 114", bottles = 2, column = 1)))
            },
        ).send { }

        assertEquals(SheetOrderMatcher.rowKey(writtenTab!!, 1, 1), callDao.byId(1)!!.sheetMatchKey)

        // Вторая открытая карточка с тем же адресом не должна закрываться
        // строкой, которую приложение записало само.
        callDao.add(record(2, "Артема 114", at = 300))
        val checks = SheetCheckRepository(
            callDao = callDao,
            aliasDao = FakeAddressAliasDao(),
            sheets = SheetsClient(),
        )
        val outcome = checks.apply(
            listOf(
                SheetTab(writtenTab!!, listOf(SheetOrderRow(rowIndex = 1, address = "Артема 114", bottles = 2, column = 1))),
            ),
        )

        assertEquals(0, outcome.closedCount)
        assertFalse(callDao.byId(2)!!.isProcessed)
    }

    @Test
    fun `дубль адреса внутри партии отправляется один раз`() = runTest {
        callDao.add(record(1, "Артема 114", at = 200))
        callDao.add(record(2, "артема, 114", at = 100))

        val sent = mutableListOf<List<SheetWriteOrder>>()
        val outcome = repository(
            writeTab = { _, orders ->
                sent.add(orders)
                SheetWriteResult.Success("tab", orders.map { SheetWrittenRow(it.address, 1) })
            },
            fetchTab = { tab ->
                SheetTab(tab, listOf(SheetOrderRow(rowIndex = 1, address = "Артема 114", bottles = 2, column = 1)))
            },
        ).send { }

        // Пишется только первый адрес; второй — дубль внутри партии.
        assertEquals(1, sent.size)
        assertEquals(listOf("Артема 114"), sent.single().map { it.address })
        assertEquals(2, outcome.total)
        assertEquals(1, outcome.duplicates)
        assertEquals(1, outcome.confirmed)
        assertTrue(callDao.byId(1)!!.isProcessed)
        assertFalse(callDao.byId(2)!!.isProcessed)
    }
}
