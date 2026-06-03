package com.chen.memorizewords.domain.account.policy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogoutDataLossPolicyTest {
    private val policy = LogoutDataLossPolicy()

    @Test
    fun `non force logout aborts when pending sync remains`() {
        assertTrue(policy.shouldAbortAfterFlush(force = false, pendingSyncCount = 1))
    }

    @Test
    fun `force logout does not abort when pending sync remains`() {
        assertFalse(policy.shouldAbortAfterFlush(force = true, pendingSyncCount = 1))
    }

    @Test
    fun `non force logout does not abort without pending sync`() {
        assertFalse(policy.shouldAbortAfterFlush(force = false, pendingSyncCount = 0))
    }
}

