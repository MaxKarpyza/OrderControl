package ru.ordercontrol.data

import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.ordercontrol.domain.BlacklistKind
import ru.ordercontrol.domain.CloseReason
import ru.ordercontrol.domain.DeliveryDay
import ru.ordercontrol.domain.SkipUpdate
import ru.ordercontrol.domain.WorkSchedule

class CallRepositoryTest {

    private val callDao = FakeCallRecordDao()
    private val blacklistDao = FakeBlacklistDao()
    private val schedule = FakeScheduleProvider()
    private val contacts = FakeContactLookup()
    private val repository = CallRepository(
        callDao = callDao,
        blacklistDao = blacklistDao,
        settings = schedule,
        contacts = contacts,
    )

    @Before
    fun setUp() {
        schedule.schedule = WorkSchedule(
            startMinute = 8 * 60,
            endMinute = 17 * 60,
            days = DayOfWeek.entries.toSet(),
        )
    }

    /** 27.10.2026, 12:00 — рабочее время. */
    private val workingNoon = 1_793_098_800_000L

    /** 27.10.2026, 22:00 — вне рабочего времени. */
    private val night = workingNoon + 10 * 60 * 60 * 1000L

    private suspend fun register(
        number: String? = "+79991234567",
        answered: Boolean = true,
        at: Long = workingNoon,
        callLogId: Long? = null,
    ): Long? = repository.registerIncoming(
        numberRaw = number,
        answered = answered,
        incomingAt = at,
        callLogId = callLogId,
    )

    @Test
    fun `answered call creates a record`() = runTest {
        val id = register()

        assertNotNull(id)
        val record = callDao.byId(id!!)!!
        assertEquals("+79991234567", record.phoneNormalized)
        assertFalse(record.isSilent)
        assertFalse(record.isProcessed)
        assertTrue(record.isAnswered!!)
    }

    @Test
    fun `missed call in working time is marked missed`() = runTest {
        val id = register(answered = false)!!

        assertFalse(callDao.byId(id)!!.isAnswered!!)
    }

    @Test
    fun `repeat answered call marks a previously missed record as answered`() = runTest {
        val id = register(answered = false)!!
        val second = register(answered = true, at = workingNoon + 60_000)

        assertEquals(id, second)
        assertTrue(callDao.byId(id)!!.isAnswered!!)
    }

    @Test
    fun `unread summary splits answered and missed records`() = runTest {
        register(answered = true)
        register(number = "+79001112233", answered = false, at = workingNoon + 1000)
        register(number = "+79002223344", answered = true, at = workingNoon + 2000)

        val summary = repository.unreadSummary()

        assertEquals(3, summary.total)
        assertEquals(2, summary.answered)
        assertEquals(1, summary.missed)
    }

    @Test
    fun `missed call outside working time creates nothing`() = runTest {
        assertNull(register(answered = false, at = night))
        assertTrue(callDao.all().isEmpty())
    }

    @Test
    fun `answered call outside working time is unprocessed`() = runTest {
        val id = register(at = night)!!

        assertFalse(callDao.byId(id)!!.isSilent)
        assertEquals(1, repository.unreadCount())
    }

    @Test
    fun `repeat call merges into the same record and keeps order data`() = runTest {
        val id = register()!!
        repository.saveOrder(
            recordId = id,
            bottles = "2 по 19 л",
            address = "Артема 114 2-314",
            nameInput = null,
            skip = SkipUpdate.Unchanged,
        )

        val later = workingNoon + 60_000
        val second = register(at = later)

        assertEquals(id, second)
        assertEquals(1, callDao.all().size)
        val record = callDao.byId(id)!!
        assertEquals("2 по 19 л", record.bottlesText)
        assertEquals("Артема 114 2-314", record.addressText)
        assertEquals(later, record.incomingAt)
    }

    @Test
    fun `hidden number never merges`() = runTest {
        val first = register(number = null)!!
        val second = register(number = null, at = workingNoon + 60_000)!!

        assertTrue(first != second)
        assertEquals(2, callDao.all().size)
    }

    @Test
    fun `permanent blacklist blocks every call`() = runTest {
        val id = register()!!
        repository.saveOrder(
            recordId = id,
            bottles = "",
            address = "",
            nameInput = null,
            skip = SkipUpdate.Set(BlacklistKind.PERMANENT),
        )

        assertNull(register(at = workingNoon + 60_000))
        assertNull(register(at = workingNoon + 120_000))
        assertEquals(1, callDao.all().size)
        assertEquals(BlacklistKind.PERMANENT, blacklistDao.find("+79991234567")?.kind)
    }

    @Test
    fun `once blacklist blocks one call and is removed`() = runTest {
        val id = register()!!
        repository.saveOrder(
            recordId = id,
            bottles = "",
            address = "",
            nameInput = null,
            skip = SkipUpdate.Set(BlacklistKind.ONCE),
        )

        assertNull(register(at = workingNoon + 60_000))
        assertNull(blacklistDao.find("+79991234567"))
        // Следующий звонок снова создаёт запись.
        assertNotNull(register(at = workingNoon + 120_000))
    }

    @Test
    fun `clearing skip removes the mark and keeps the record open`() = runTest {
        val id = register()!!
        repository.saveOrder(
            recordId = id,
            bottles = "",
            address = "",
            nameInput = null,
            skip = SkipUpdate.Set(BlacklistKind.PERMANENT),
        )
        repository.saveOrder(
            recordId = id,
            bottles = "",
            address = "",
            nameInput = null,
            skip = SkipUpdate.Clear,
        )

        assertNull(blacklistDao.find("+79991234567"))
        assertFalse(callDao.byId(id)!!.isSkipped)
    }

    @Test
    fun `saving the form without touching the mark keeps it`() = runTest {
        val id = register()!!
        repository.saveOrder(
            recordId = id,
            bottles = "",
            address = "",
            nameInput = null,
            skip = SkipUpdate.Set(BlacklistKind.PERMANENT),
        )
        repository.saveOrder(
            recordId = id,
            bottles = "3",
            address = "Мира 11",
            nameInput = null,
            skip = SkipUpdate.Unchanged,
        )

        assertEquals(BlacklistKind.PERMANENT, blacklistDao.find("+79991234567")?.kind)
        assertTrue(callDao.byId(id)!!.isSkipped)
    }

    @Test
    fun `contact name is taken when the record is created`() = runTest {
        contacts.put("+79991234567", "Иван")

        val id = register()!!

        assertEquals("Иван", callDao.byId(id)!!.displayName)
    }

    @Test
    fun `saving a name creates the contact and shows it in the record`() = runTest {
        val id = register()!!

        val failed = repository.saveOrder(
            recordId = id,
            bottles = "1",
            address = "Мира 11",
            nameInput = "Пётр",
            skip = SkipUpdate.Unchanged,
        )

        assertFalse(failed)
        assertEquals(listOf("Пётр" to "+79991234567"), contacts.created)
        assertEquals("Пётр", callDao.byId(id)!!.displayName)
    }

    @Test
    fun `failed contact creation is reported but the order is saved`() = runTest {
        val locked = FakeContactLookup(canCreate = false)
        val repo = CallRepository(callDao, blacklistDao, schedule, locked)
        val id = repo.registerIncoming(
            numberRaw = "+79991234567",
            answered = true,
            incomingAt = workingNoon,
            callLogId = null,
        )!!

        val failed = repo.saveOrder(
            recordId = id,
            bottles = "1",
            address = "Мира 11",
            nameInput = "Пётр",
            skip = SkipUpdate.Unchanged,
        )

        assertTrue(failed)
        assertEquals("1", callDao.byId(id)!!.bottlesText)
        assertEquals("Мира 11", callDao.byId(id)!!.addressText)
    }

    @Test
    fun `address without name does not create a contact`() = runTest {
        val id = register()!!

        repository.saveOrder(
            recordId = id,
            bottles = "2",
            address = "Мира 11",
            nameInput = "",
            skip = SkipUpdate.Unchanged,
        )

        assertTrue(contacts.created.isEmpty())
        assertEquals("Мира 11", callDao.byId(id)!!.addressText)
    }

    @Test
    fun `processed records are closed with done reason`() = runTest {
        register()
        register(number = "+79001112233", at = workingNoon + 1000)

        repository.markAllProcessed()

        assertTrue(callDao.all().all { it.isProcessed })
        assertTrue(callDao.all().all { it.closeReason == CloseReason.DONE })
        assertTrue(callDao.all().all { it.processedAt != null })
    }

    @Test
    fun `restored record returns to unprocessed lists`() = runTest {
        val id = register()!!
        repository.markProcessed(id)
        assertTrue(callDao.byId(id)!!.isProcessed)

        repository.restoreProcessed(id)

        val record = callDao.byId(id)!!
        assertFalse(record.isProcessed)
        assertNull(record.processedAt)
        assertNull(record.closeReason)
        assertNull(record.sheetMatchKey)
    }

    @Test
    fun `night and day calls are both counted`() = runTest {
        register(at = night)
        register(at = workingNoon, number = "+79001112233")

        assertEquals(2, repository.unreadCount())
        assertEquals(2, callDao.all().count { !it.isProcessed })
    }

    @Test
    fun `reconcile closes records missing from the call log`() = runTest {
        register(number = "+79991234567", callLogId = 100)
        register(number = "+79001112233", callLogId = 200, at = workingNoon + 1000)

        repository.reconcileWithCallLog(existingCallLogIds = setOf(100))

        val closed = callDao.all().single { it.callLogId == 200L }
        assertTrue(closed.isProcessed)
        assertEquals(CloseReason.DELETED_FROM_CALLLOG, closed.closeReason)
        assertFalse(callDao.all().single { it.callLogId == 100L }.isProcessed)
    }

    @Test
    fun `call log id is attached to a record created from the call state`() = runTest {
        // Запись создаётся сразу после звонка, без callLogId.
        val id = register(callLogId = null)!!
        assertNull(callDao.byId(id)!!.callLogId)

        repository.attachCallLogId(recordId = id, callLogId = 777)

        assertEquals(777L, callDao.byId(id)!!.callLogId)
        // Запись не закрылась и осталась необработанной.
        assertFalse(callDao.byId(id)!!.isProcessed)
    }

    @Test
    fun `already attached call log id is not overwritten`() = runTest {
        val id = register(callLogId = 100)!!

        repository.attachCallLogId(recordId = id, callLogId = 999)

        assertEquals(100L, callDao.byId(id)!!.callLogId)
    }

    @Test
    fun `another order creates a second card for the same client`() = runTest {
        val first = register()!!
        repository.saveOrder(
            recordId = first,
            bottles = "2",
            address = "Мира 11-7",
            nameInput = null,
            skip = SkipUpdate.Unchanged,
        )

        val second = repository.addAnotherOrder(first)!!

        val firstRecord = callDao.byId(first)!!
        val secondRecord = callDao.byId(second)!!
        assertEquals("Мира 11-7", firstRecord.addressText)
        assertEquals("", secondRecord.addressText)
        assertEquals("", secondRecord.bottlesText)
        assertEquals("+79991234567", secondRecord.phoneNormalized)
        assertFalse(secondRecord.isProcessed)
        // Обе карточки открыты: в списке два отдельных заказа.
        assertEquals(2, callDao.openRecords().size)
    }

    @Test
    fun `another order does not share the call log id`() = runTest {
        val first = register(callLogId = 100)!!

        val second = repository.addAnotherOrder(first)!!

        assertNull(callDao.byId(second)!!.callLogId)
    }

    @Test
    fun `previous order of the second card shows the first address`() = runTest {
        val first = register()!!
        repository.saveOrder(
            recordId = first,
            bottles = "2",
            address = "Мира 11-7",
            nameInput = null,
            skip = SkipUpdate.Unchanged,
        )
        val second = repository.addAnotherOrder(first)!!

        val previous = callDao.previousOrder("+79991234567", second)

        assertEquals("Мира 11-7", previous?.addressText)
    }

    @Test
    fun `names are refreshed from the book without closing records`() = runTest {        val id = register()!!
        contacts.put("+79991234567", "Иван")

        repository.refreshNamesFromContacts()

        val record = callDao.byId(id)!!
        assertEquals("Иван", record.displayName)
        assertFalse(record.isProcessed)
    }

    @Test
    fun `empty address stays empty and falls back to the contact name`() = runTest {
        contacts.put("+79991234567", "Иван")
        val id = register()!!

        repository.saveOrder(
            recordId = id,
            bottles = "2",
            address = "",
            nameInput = null,
            skip = SkipUpdate.Unchanged,
        )

        val record = callDao.byId(id)!!
        assertEquals("", record.addressText)
        assertEquals("Иван", record.deliveryAddress)
    }

    @Test
    fun `delivery day is saved with the order`() = runTest {
        val id = register()!!

        repository.saveOrder(
            recordId = id,
            bottles = "2",
            address = "Мира 11",
            nameInput = null,
            skip = SkipUpdate.Unchanged,
            deliveryDay = DeliveryDay.DAY_AFTER_TOMORROW,
        )

        val record = callDao.byId(id)!!
        assertTrue(record.isNextDay)
        assertEquals(DeliveryDay.DAY_AFTER_TOMORROW, record.deliveryDay)
    }

    @Test
    fun `another order starts without the delivery day`() = runTest {
        val first = register()!!
        repository.saveOrder(
            recordId = first,
            bottles = "2",
            address = "Мира 11",
            nameInput = null,
            skip = SkipUpdate.Unchanged,
            deliveryDay = DeliveryDay.TOMORROW,
        )

        val second = repository.addAnotherOrder(first)!!

        assertFalse(callDao.byId(second)!!.isNextDay)
        assertNull(callDao.byId(second)!!.deliveryDay)
    }

    @Test
    fun `history shows records processed by the operator newest first`() = runTest {
        val newer = register(number = "+79001112233", at = workingNoon + 1000)!!
        val older = register(at = workingNoon)!!
        val removed = register(number = "+79005556677", at = workingNoon + 2000)!!

        callDao.closeAll(at = 5_000L, reason = CloseReason.DONE)
        // Запись, убранная из журнала звонков, обработана не оператором.
        callDao.update(callDao.byId(removed)!!.copy(closeReason = CloseReason.DELETED_FROM_CALLLOG))

        val history = repository.observeHistory().first()

        assertEquals(listOf(newer, older), history.map { it.id })
    }
}
