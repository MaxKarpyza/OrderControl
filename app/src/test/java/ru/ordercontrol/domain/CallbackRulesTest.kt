package ru.ordercontrol.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Правило «перезвон снимает отметку „пропущен“». */
class CallbackRulesTest {

    private val phone = "+79991234567"
    private val missedAt = 1_793_098_800_000L

    @Test
    fun `connected callback answers the missed call`() {
        val callbacks = listOf(CallbackCall(phone = phone, date = missedAt + 60_000, durationSec = 30))

        assertTrue(CallbackRules.answeredByCallback(phone, missedAt, callbacks))
    }

    @Test
    fun `callback without answer does not answer`() {
        val callbacks = listOf(CallbackCall(phone = phone, date = missedAt + 60_000, durationSec = 0))

        assertFalse(CallbackRules.answeredByCallback(phone, missedAt, callbacks))
    }

    @Test
    fun `callback before the missed call does not answer`() {
        val callbacks = listOf(CallbackCall(phone = phone, date = missedAt - 60_000, durationSec = 30))

        assertFalse(CallbackRules.answeredByCallback(phone, missedAt, callbacks))
    }

    @Test
    fun `callback to another number does not answer`() {
        val callbacks = listOf(CallbackCall(phone = "+79997654321", date = missedAt + 60_000, durationSec = 30))

        assertFalse(CallbackRules.answeredByCallback(phone, missedAt, callbacks))
    }

    @Test
    fun `without callbacks nothing changes`() {
        assertFalse(CallbackRules.answeredByCallback(phone, missedAt, emptyList()))
    }
}
