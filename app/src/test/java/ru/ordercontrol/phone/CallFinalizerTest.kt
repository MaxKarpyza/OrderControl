package ru.ordercontrol.phone

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.ordercontrol.data.CallRepository
import ru.ordercontrol.data.FakeBlacklistDao
import ru.ordercontrol.data.FakeCallRecordDao
import ru.ordercontrol.data.FakeContactLookup
import ru.ordercontrol.data.FakeScheduleProvider

class CallFinalizerTest {

    private val callDao = FakeCallRecordDao()
    private val callLog = FakeCallLogSource()
    private val repository = CallRepository(
        callDao = callDao,
        blacklistDao = FakeBlacklistDao(),
        settings = FakeScheduleProvider(),
        contacts = FakeContactLookup(),
    )
    private val finalizer = CallFinalizer(calls = repository, callLog = callLog)

    private val callAt = 1_793_098_800_000L

    @Before
    fun setUp() {
        callDao.all().forEach { }
    }

    @Test
    fun `known number is recorded immediately`() = runTest {
        val id = finalizer.resolve(
            CallSession(subscriptionId = 0, number = "+79991234567", startedAt = callAt, answered = true),
        )

        assertNotNull(id)
        assertEquals("+79991234567", callDao.byId(id!!)!!.phoneNormalized)
        assertNull(callDao.byId(id)!!.callLogId)
    }

    @Test
    fun `number missing in phone state is taken from the call log`() = runTest {
        // Прошивка не отдала номер — так ведёт себя телефон оператора.
        callLog.add("+79991234567", date = callAt + 1000)

        val id = finalizer.resolve(
            CallSession(subscriptionId = 0, number = null, startedAt = callAt, answered = true),
        )

        val record = callDao.byId(id!!)!!
        assertEquals("+79991234567", record.phoneNormalized)
        assertEquals(1L, record.callLogId)
    }

    @Test
    fun `number is taken from the log even when a different call is in the window`() = runTest {
        callLog.add("+79001112233", date = callAt - 4000)
        callLog.add("+79991234567", date = callAt + 1000)

        val id = finalizer.resolve(
            CallSession(subscriptionId = 0, number = null, startedAt = callAt, answered = true),
        )

        assertEquals("+79991234567", callDao.byId(id!!)!!.phoneNormalized)
    }

    @Test
    fun `missed call keeps the log type`() = runTest {
        callLog.add("+79991234567", date = callAt + 1000, answered = false)

        val id = finalizer.resolve(
            CallSession(subscriptionId = 0, number = null, startedAt = callAt, answered = false),
        )

        assertEquals("+79991234567", callDao.byId(id!!)!!.phoneNormalized)
    }

    @Test
    fun `hidden number without a log entry is recorded as hidden`() = runTest {
        val id = finalizer.resolve(
            CallSession(subscriptionId = 0, number = null, startedAt = callAt, answered = true),
        )

        assertNull(callDao.byId(id!!)!!.phoneNormalized)
    }

    @Test
    fun `without call log permission the call is still recorded`() = runTest {
        callLog.permission = false
        callLog.add("+79991234567", date = callAt + 1000)

        val id = finalizer.resolve(
            CallSession(subscriptionId = 0, number = null, startedAt = callAt, answered = true),
        )

        assertNotNull(id)
        assertNull(callDao.byId(id!!)!!.phoneNormalized)
    }

    @Test
    fun `nearest call is taken, not the newest one`() = runTest {
        // В журнале есть звонок со сбитыми часами — он не должен «прилипнуть».
        callLog.add("+79001112233", date = callAt + 10 * 60 * 1000)
        callLog.add("+79991234567", date = callAt + 1000)

        val id = finalizer.resolve(
            CallSession(subscriptionId = 0, number = null, startedAt = callAt, answered = true),
        )

        assertEquals("+79991234567", callDao.byId(id!!)!!.phoneNormalized)
    }

    @Test
    fun `call log entry of a previous call is not reused`() = runTest {
        val oldId = callLog.add("+79991234567", date = callAt + 1000)
        repository.registerIncoming(
            numberRaw = "+79991234567",
            answered = true,
            incomingAt = callAt + 1000,
            callLogId = oldId,
        )

        // Новый звонок с тем же номером, журнал ещё не обновился.
        val id = finalizer.resolve(
            CallSession(subscriptionId = 0, number = null, startedAt = callAt + 60_000, answered = true),
        )

        // В окне нового звонка записей нет, поэтому запись создаётся без номера,
        // но старая запись не переиспользуется.
        assertEquals(2, callDao.all().size)
        assertTrue(callDao.byId(id!!)!!.callLogId == null)
    }
}
