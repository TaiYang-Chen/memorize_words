package com.chen.memorizewords.data.sync.repository.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncBackoffPolicyTest {

    @Test
    fun `backoff applies bounded jitter`() {
        assertEquals(15_000L, syncOutboxBackoffDelayMillis(1, jitterFactor = 0.5))
        assertEquals(45_000L, syncOutboxBackoffDelayMillis(1, jitterFactor = 1.5))
        assertEquals(900_000L, syncOutboxBackoffDelayMillis(4, jitterFactor = 0.5))
    }
}
