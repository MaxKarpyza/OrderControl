package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogReconcilerTest {

    @Test
    fun `records with existing call log entries stay open`() {
        val open = listOf(OpenCallRef(recordId = 1, callLogId = 100))
        val toClose = CallLogReconciler.idsToClose(open, existingCallLogIds = setOf(100))
        assertTrue(toClose.isEmpty())
    }

    @Test
    fun `record deleted from call log is closed`() {
        val open = listOf(OpenCallRef(recordId = 7, callLogId = 100))
        val toClose = CallLogReconciler.idsToClose(open, existingCallLogIds = emptySet())
        assertEquals(listOf(7L), toClose)
    }

    @Test
    fun `only missing records are closed`() {
        val open = listOf(
            OpenCallRef(recordId = 1, callLogId = 100),
            OpenCallRef(recordId = 2, callLogId = 200),
            OpenCallRef(recordId = 3, callLogId = 300),
        )
        val toClose = CallLogReconciler.idsToClose(open, existingCallLogIds = setOf(100, 300))
        assertEquals(listOf(2L), toClose)
    }

    @Test
    fun `nothing to close when there are no open records`() {
        assertTrue(CallLogReconciler.idsToClose(emptyList(), emptySet()).isEmpty())
    }

    @Test
    fun `blacklist policy distinguishes permanent and once`() {
        assertFalse(BlacklistPolicy.suppressesCall(null))
        assertTrue(BlacklistPolicy.suppressesCall(BlacklistKind.PERMANENT))
        assertTrue(BlacklistPolicy.suppressesCall(BlacklistKind.ONCE))

        assertFalse(BlacklistPolicy.consumesAfterCall(BlacklistKind.PERMANENT))
        assertTrue(BlacklistPolicy.consumesAfterCall(BlacklistKind.ONCE))
    }
}
