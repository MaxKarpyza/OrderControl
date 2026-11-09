package ru.ordercontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRulesTest {

    @Test
    fun `answered call is always recorded`() {
        assertTrue(CallRules.shouldCreateRecord(answered = true, working = true))
        assertTrue(CallRules.shouldCreateRecord(answered = true, working = false))
    }

    @Test
    fun `missed call is recorded only during working time`() {
        assertTrue(CallRules.shouldCreateRecord(answered = false, working = true))
        assertFalse(CallRules.shouldCreateRecord(answered = false, working = false))
    }

    @Test
    fun `missed call outside working time is ignored`() {
        val plan = CallRules.plan(
            answered = false,
            working = false,
            blacklisted = false,
            hasOpenRecord = false,
            hiddenNumber = false,
        )
        assertEquals(RegistrationPlan.Ignore, plan)
    }

    @Test
    fun `blacklisted number is ignored even when answered`() {
        val plan = CallRules.plan(
            answered = true,
            working = true,
            blacklisted = true,
            hasOpenRecord = true,
            hiddenNumber = false,
        )
        assertEquals(RegistrationPlan.Ignore, plan)
    }

    @Test
    fun `repeat call of open record merges`() {
        val plan = CallRules.plan(
            answered = true,
            working = true,
            blacklisted = false,
            hasOpenRecord = true,
            hiddenNumber = false,
        )
        assertEquals(RegistrationPlan.Merge, plan)
    }

    @Test
    fun `hidden number never merges`() {
        val plan = CallRules.plan(
            answered = true,
            working = true,
            blacklisted = false,
            hasOpenRecord = true,
            hiddenNumber = true,
        )
        assertEquals(RegistrationPlan.Create, plan)
    }

    @Test
    fun `answered call outside working time creates a record`() {
        val plan = CallRules.plan(
            answered = true,
            working = false,
            blacklisted = false,
            hasOpenRecord = false,
            hiddenNumber = false,
        )
        assertEquals(RegistrationPlan.Create, plan)
    }

    @Test
    fun `repeat night call merges into open record`() {
        val plan = CallRules.plan(
            answered = true,
            working = false,
            blacklisted = false,
            hasOpenRecord = true,
            hiddenNumber = false,
        )
        assertEquals(RegistrationPlan.Merge, plan)
    }
}
