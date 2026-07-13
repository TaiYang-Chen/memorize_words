package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.domain.sync.SyncFailureKind
import com.chen.memorizewords.core.network.remote.HttpStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DataSyncConflictPolicyTest {

    @Test
    fun `explicit event conflict is blocked instead of blindly retried`() {
        val decision = classifySyncOutboxFailure(
            SyncEventConflictException("revision mismatch")
        )

        assertFalse(decision.shouldRetry)
        assertEquals(SyncFailureKind.CONFLICT, decision.failureKind)
        assertEquals("BLOCKED|conflict|revision mismatch", decision.persistedMessage)
    }

    @Test
    fun `rate limit honors server retry after`() {
        val decision = classifySyncOutboxFailure(
            HttpStatusException(
                code = 429,
                message = "too many requests",
                retryAfterSeconds = 75L
            )
        )

        assertEquals(SyncFailureKind.RATE_LIMIT, decision.failureKind)
        assertEquals(75_000L, decision.retryAfterMillis)
    }
}
