package ru.ordercontrol.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.domain.CloseReason

class HistorySyncRepositoryTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private val callDao = FakeCallRecordDao()
    private val sharedDao = FakeSharedHistoryDao()
    private val syncer = FakeHistorySyncer(HistorySyncResult.Success(emptyList(), emptySet()))

    private val operatorProvider = FakeOperatorProvider(Operator(id = "опер_37-A3F", name = null))

    private val repository = HistorySyncRepository(
        callDao = callDao,
        sharedDao = sharedDao,
        client = syncer,
        operators = operatorProvider,
        now = { now },
    )

    private fun closed(
        id: Long,
        phone: String? = "+7999000000$id",
        processedAt: Long = now - 1_000,
        reason: CloseReason = CloseReason.DONE,
        address: String = "Малахова 1-6$id",
    ) = CallRecord(
        id = id,
        phoneNormalized = phone,
        incomingAt = processedAt - 60_000,
        bottlesText = "2",
        addressText = address,
        isProcessed = true,
        processedAt = processedAt,
        closeReason = reason,
        comment = "звони",
        district = "Мирный",
    )

    @Test
    fun `своя обработанная запись уходит в таблицу и отмечается отправленной`() = runTest {
        val id = callDao.add(closed(id = 1))

        val outcome = repository.sync()

        assertEquals(1, syncer.lastPush.size)
        val item = syncer.lastPush.single()
        assertEquals("опер_37-A3F:$id:${now - 1_000}", item.id)
        assertEquals("+79990000001", item.phone)
        assertEquals("Малахова 1-61", item.address)
        assertEquals("2", item.bottles)
        assertEquals("звони", item.comment)
        assertEquals("Мирный", item.district)
        assertEquals(now - 1_000, item.processedAt)
        assertEquals(now, callDao.byId(id)?.historySentAt)
        assertEquals(1, outcome.pushed)
    }

    @Test
    fun `отправленные записи повторно не уходят`() = runTest {
        callDao.add(closed(id = 1))
        repository.sync()

        syncer.lastPush = emptyList()
        repository.sync()

        assertTrue(syncer.lastPush.isEmpty())
    }

    @Test
    fun `своя строка из таблицы не попадает в чужую историю`() = runTest {
        val id = callDao.add(closed(id = 1))
        val mine = SharedHistoryRow(
            id = "опер_37-A3F:$id:${now - 1_000}",
            author = "опер_37-A3F",
            phone = "+79990000001",
            address = "Малахова 1-61",
            district = "Мирный",
            bottles = "2",
            comment = "",
            processedAt = now - 1_000,
        )
        val other = mine.copy(
            id = "опер_12-BCD:9:${now - 2_000}",
            author = "опер_12-BCD",
            phone = "+79990000009",
            address = "Козыря 21-77",
            processedAt = now - 2_000,
        )
        syncer.result = HistorySyncResult.Success(
            rows = listOf(mine, other),
            accepted = setOf(mine.id),
        )

        val outcome = repository.sync()

        assertEquals(1, outcome.received)
        val stored = sharedDao.all().single()
        assertEquals("опер_12-BCD:9:${now - 2_000}", stored.remoteId)
        assertEquals("опер_12-BCD", stored.author)
        assertEquals("Козыря 21-77", stored.address)
        assertEquals(now, stored.syncedAt)
    }

    @Test
    fun `вместо технического имени уходит ФИО из настроек`() = runTest {
        operatorProvider.operator = Operator(id = "опер_37-A3F", name = "Иванов Иван")
        syncWith()

        assertEquals("Иванов Иван", syncer.lastOperator)
    }

    @Test
    fun `запись без телефона в общую историю не уходит`() = runTest {
        callDao.add(closed(id = 1, phone = null))

        repository.sync()

        assertTrue(syncer.lastPush.isEmpty())
    }

    @Test
    fun `запись старше срока хранения не уходит`() = runTest {
        callDao.add(closed(id = 1, processedAt = now - 3 * day))

        repository.sync()

        assertTrue(syncer.lastPush.isEmpty())
    }

    @Test
    fun `скрипт без общей истории ничего не отмечает отправленным`() = runTest {
        val id = callDao.add(closed(id = 1))
        syncer.result = HistorySyncResult.Unsupported

        val outcome = repository.sync()

        assertTrue(outcome.outdatedScript)
        assertNull(callDao.byId(id)?.historySentAt)
        assertTrue(sharedDao.all().isEmpty())
    }

    @Test
    fun `отмечаются только строки, которые таблица подтвердила`() = runTest {
        val first = callDao.add(closed(id = 1))
        val second = callDao.add(closed(id = 2))
        syncer.result = HistorySyncResult.Success(
            rows = emptyList(),
            accepted = setOf("опер_37-A3F:$first:${now - 1_000}"),
        )

        repository.sync()

        assertEquals(now, callDao.byId(first)?.historySentAt)
        assertNull(callDao.byId(second)?.historySentAt)
    }

    @Test
    fun `ошибка сети не ломает синхронизацию и не отмечает строки`() = runTest {
        val id = callDao.add(closed(id = 1))
        syncer.result = HistorySyncResult.NoNetwork

        val outcome = repository.sync()

        assertEquals("нет сети", outcome.error)
        assertNull(callDao.byId(id)?.historySentAt)
    }

    @Test
    fun `выгрузка отправляет только свои ещё не отправленные записи`() = runTest {
        val unsent = callDao.add(closed(id = 1))
        val sent = callDao.add(closed(id = 2, processedAt = now - 2_000))
        callDao.markHistorySent(ids = listOf(sent), at = now - 1_000)

        val outcome = repository.export()

        assertEquals(1, syncer.lastPush.size)
        assertEquals("опер_37-A3F:$unsent:${now - 1_000}", syncer.lastPush.single().id)
        assertEquals(1, outcome.pushed)
        assertEquals(now, callDao.byId(unsent)?.historySentAt)
    }

    @Test
    fun `выгрузка не забирает чужие записи`() = runTest {
        callDao.add(closed(id = 1))
        syncer.result = HistorySyncResult.Success(
            rows = listOf(
                SharedHistoryRow(
                    id = "опер_12-BCD:9:${now - 2_000}",
                    author = "опер_12-BCD",
                    phone = "+79990000009",
                    address = "Козыря 21-77",
                    district = "Мирный",
                    bottles = "1",
                    comment = "",
                    processedAt = now - 2_000,
                ),
            ),
            accepted = emptySet(),
        )

        val outcome = repository.export()

        assertEquals(0, outcome.received)
        assertTrue(sharedDao.all().isEmpty())
    }

    @Test
    fun `выгрузка без новых записей не ходит в сеть`() = runTest {
        callDao.add(closed(id = 1, phone = null))

        val outcome = repository.export()

        assertTrue(syncer.lastPush.isEmpty())
        assertEquals(0, outcome.pushed)
    }

    @Test
    fun `выгрузка отмечает только подтверждённые таблицей строки`() = runTest {
        val first = callDao.add(closed(id = 1))
        val second = callDao.add(closed(id = 2))
        syncer.result = HistorySyncResult.Success(
            rows = emptyList(),
            accepted = setOf("опер_37-A3F:$first:${now - 1_000}"),
        )

        repository.export()

        assertEquals(now, callDao.byId(first)?.historySentAt)
        assertNull(callDao.byId(second)?.historySentAt)
    }

    @Test
    fun `выгрузка не отправляет записи старше срока хранения и ручные без телефона`() = runTest {
        callDao.add(closed(id = 1, processedAt = now - 3 * day))
        callDao.add(closed(id = 2, phone = null))

        repository.export()

        assertTrue(syncer.lastPush.isEmpty())
    }

    private suspend fun syncWith() {
        callDao.add(closed(id = 1))
        repository.sync()
    }
}
