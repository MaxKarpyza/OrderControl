package ru.ordercontrol.phone

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ordercontrol.data.CallRepository
import ru.ordercontrol.data.FakeBlacklistDao
import ru.ordercontrol.data.FakeCallRecordDao
import ru.ordercontrol.data.FakeContactLookup
import ru.ordercontrol.data.FakeScheduleProvider

class CallCatchUpTest {

    private val callDao = FakeCallRecordDao()
    private val callLog = FakeCallLogSource()
    private val repository = CallRepository(
        callDao = callDao,
        blacklistDao = FakeBlacklistDao(),
        settings = FakeScheduleProvider(),
        contacts = FakeContactLookup(),
    )

    private val callAt = 1_793_098_800_000L

    @Test
    fun `existing record is not restored twice`() = runTest {
        val entryId = callLog.add("+79991234567", date = callAt)
        repository.registerIncoming(
            numberRaw = "+79991234567",
            answered = true,
            incomingAt = callAt,
            callLogId = entryId,
        )

        // Восстановление не должно создать вторую запись.
        val same = callLog.latestIncomingSince(callAt - 1000).first()
        assertTrue(repository.isCallLogIdKnown(same.id))
    }

    @Test
    fun `record without callLogId is recognised by number and time`() = runTest {
        // Запись создана сразу после звонка, журнал отстал.
        repository.registerIncoming(
            numberRaw = "+79991234567",
            answered = true,
            incomingAt = callAt,
            callLogId = null,
        )

        assertTrue(repository.hasRecordNear("+79991234567", callAt, 120_000))
        assertEquals(1, callDao.all().size)
    }

    @Test
    fun `processed record is still recognised`() = runTest {
        val id = repository.registerIncoming(
            numberRaw = "+79991234567",
            answered = true,
            incomingAt = callAt,
            callLogId = null,
        )!!
        repository.markAllProcessed()

        assertTrue(callDao.byId(id)!!.isProcessed)
        assertTrue(repository.hasRecordNear("+79991234567", callAt, 120_000))
    }

    @Test
    fun `different call is not confused with an existing one`() = runTest {
        repository.registerIncoming(
            numberRaw = "+79991234567",
            answered = true,
            incomingAt = callAt,
            callLogId = null,
        )

        assertTrue(!repository.hasRecordNear("+79991234567", callAt + 30 * 60_000, 120_000))
    }
}
