package ru.ordercontrol.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ordercontrol.domain.CallbackCall

/**
 * Перезвон клиенту снимает отметку «пропущен» — но только если разговор
 * действительно состоялся.
 */
class CallCallbackTest {

    private val callDao = FakeCallRecordDao()
    private val repository = CallRepository(
        callDao = callDao,
        blacklistDao = FakeBlacklistDao(),
        settings = FakeScheduleProvider(),
        contacts = FakeContactLookup(),
    )

    private val phone = "+79991234567"
    private val missedAt = 1_793_098_800_000L

    private suspend fun missedRecord(): Long = repository.registerIncoming(
        numberRaw = phone,
        answered = false,
        incomingAt = missedAt,
        callLogId = null,
    )!!

    @Test
    fun `connected callback clears the missed mark`() = runTest {
        val id = missedRecord()
        assertEquals(false, callDao.byId(id)!!.isAnswered)

        val marked = repository.markAnsweredByCallback(
            listOf(CallbackCall(phone = phone, date = missedAt + 60_000, durationSec = 42)),
        )

        assertEquals(1, marked)
        assertEquals(true, callDao.byId(id)!!.isAnswered)
    }

    @Test
    fun `callback without answer keeps the missed mark`() = runTest {
        val id = missedRecord()

        val marked = repository.markAnsweredByCallback(
            listOf(CallbackCall(phone = phone, date = missedAt + 60_000, durationSec = 0)),
        )

        assertEquals(0, marked)
        assertEquals(false, callDao.byId(id)!!.isAnswered)
    }

    @Test
    fun `callback made before the missed call does not count`() = runTest {
        val id = missedRecord()

        val marked = repository.markAnsweredByCallback(
            listOf(CallbackCall(phone = phone, date = missedAt - 60_000, durationSec = 42)),
        )

        assertEquals(0, marked)
        assertEquals(false, callDao.byId(id)!!.isAnswered)
    }

    @Test
    fun `callback to another number does not count`() = runTest {
        val id = missedRecord()

        val marked = repository.markAnsweredByCallback(
            listOf(CallbackCall(phone = "+79997654321", date = missedAt + 60_000, durationSec = 42)),
        )

        assertEquals(0, marked)
        assertEquals(false, callDao.byId(id)!!.isAnswered)
    }

    @Test
    fun `missed counter drops after the callback`() = runTest {
        missedRecord()
        assertEquals(1, repository.unreadSummary().missed)

        repository.markAnsweredByCallback(
            listOf(CallbackCall(phone = phone, date = missedAt + 60_000, durationSec = 42)),
        )

        assertEquals(0, repository.unreadSummary().missed)
        assertEquals(1, repository.unreadSummary().total)
    }

    @Test
    fun `processed record is not reopened by the callback`() = runTest {
        val id = missedRecord()
        repository.markProcessed(id)

        val marked = repository.markAnsweredByCallback(
            listOf(CallbackCall(phone = phone, date = missedAt + 60_000, durationSec = 42)),
        )

        assertEquals(0, marked)
        assertTrue(callDao.byId(id)!!.isProcessed)
    }
}
