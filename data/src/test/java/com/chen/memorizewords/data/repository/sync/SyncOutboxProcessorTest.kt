package com.chen.memorizewords.data.repository.sync

import com.chen.memorizewords.data.remote.HttpStatusException
import com.chen.memorizewords.data.remote.UnauthorizedException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncOutboxProcessorTest {

    @Test
    fun `classifySyncOutboxFailure marks unauthorized as retryable auth`() {
        val decision = classifySyncOutboxFailure(UnauthorizedException("token expired"))

        assertTrue(decision.shouldRetry)
        assertEquals(SyncOutboxFailureKind.AUTH, decision.failureKind)
        assertEquals("RETRY|auth|token expired", decision.persistedMessage)
    }

    @Test
    fun `classifySyncOutboxFailure marks http 400 as blocked client error`() {
        val decision = classifySyncOutboxFailure(HttpStatusException(400, "bad request"))

        assertFalse(decision.shouldRetry)
        assertEquals(SyncOutboxFailureKind.CLIENT, decision.failureKind)
        assertEquals("TERMINAL|http:400|bad request", decision.persistedMessage)
    }

    @Test
    fun `classifySyncOutboxFailure marks http 429 as retryable rate limit`() {
        val decision = classifySyncOutboxFailure(HttpStatusException(429, "slow down"))

        assertTrue(decision.shouldRetry)
        assertEquals(SyncOutboxFailureKind.RATE_LIMIT, decision.failureKind)
        assertEquals("RETRY|http:429|slow down", decision.persistedMessage)
    }

    @Test
    fun `classifySyncOutboxFailure marks http 500 as retryable server error`() {
        val decision = classifySyncOutboxFailure(HttpStatusException(503, "unavailable"))

        assertTrue(decision.shouldRetry)
        assertEquals(SyncOutboxFailureKind.SERVER, decision.failureKind)
        assertEquals("RETRY|http:503|unavailable", decision.persistedMessage)
    }

    @Test
    fun `classifySyncOutboxFailure marks io as retryable network error`() {
        val decision = classifySyncOutboxFailure(IOException("socket timeout"))

        assertTrue(decision.shouldRetry)
        assertEquals(SyncOutboxFailureKind.NETWORK, decision.failureKind)
        assertEquals("RETRY|io|socket timeout", decision.persistedMessage)
    }

    @Test
    fun `syncOutboxBackoffDelayMillis follows configured schedule`() {
        assertEquals(30_000L, syncOutboxBackoffDelayMillis(1))
        assertEquals(120_000L, syncOutboxBackoffDelayMillis(2))
        assertEquals(600_000L, syncOutboxBackoffDelayMillis(3))
        assertEquals(1_800_000L, syncOutboxBackoffDelayMillis(4))
        assertEquals(1_800_000L, syncOutboxBackoffDelayMillis(99))
    }
}
