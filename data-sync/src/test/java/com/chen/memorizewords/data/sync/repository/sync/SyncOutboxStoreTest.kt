package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncOutboxStoreTest {

    @Test
    fun `buildQueuedOutboxEntity creates queued record for new payload`() {
        val entity = buildQueuedOutboxEntity(
            existing = null,
            bizType = SyncOutboxBizType.STUDY_PLAN,
            bizKey = "study_plan",
            operation = SyncOutboxOperation.UPSERT,
            payload = "{\"dailyNewWords\":10}",
            updatedAt = 1234L
        )

        assertEquals(0L, entity.id)
        assertEquals(SyncOutboxState.QUEUED, entity.state)
        assertEquals(0, entity.retryCount)
        assertEquals(0L, entity.lastAttemptAt)
        assertEquals(1234L, entity.nextRetryAt)
        assertNull(entity.lastError)
        assertNull(entity.failureKind)
        assertNull(entity.leaseToken)
        assertEquals(0L, entity.leaseExpiresAt)
    }

    @Test
    fun `buildQueuedOutboxEntity resets blocked record when same biz key updates`() {
        val existing = SyncOutboxEntity(
            id = 42L,
            bizType = SyncOutboxBizType.STUDY_PLAN,
            bizKey = "study_plan",
            operation = SyncOutboxOperation.UPSERT,
            payload = "{\"dailyNewWords\":5}",
            state = SyncOutboxState.BLOCKED,
            retryCount = 7,
            lastError = "TERMINAL|http:400|bad request",
            failureKind = SyncOutboxFailureKind.CLIENT,
            lastAttemptAt = 999L,
            nextRetryAt = 888L,
            leaseToken = "lease-old",
            leaseExpiresAt = 777L,
            updatedAt = 666L
        )

        val entity = buildQueuedOutboxEntity(
            existing = existing,
            bizType = SyncOutboxBizType.STUDY_PLAN,
            bizKey = "study_plan",
            operation = SyncOutboxOperation.UPSERT,
            payload = "{\"dailyNewWords\":20}",
            updatedAt = 2222L
        )

        assertEquals(42L, entity.id)
        assertEquals(SyncOutboxState.QUEUED, entity.state)
        assertEquals(0, entity.retryCount)
        assertEquals(0L, entity.lastAttemptAt)
        assertEquals(2222L, entity.nextRetryAt)
        assertNull(entity.lastError)
        assertNull(entity.failureKind)
        assertNull(entity.leaseToken)
        assertEquals(0L, entity.leaseExpiresAt)
        assertEquals("{\"dailyNewWords\":20}", entity.payload)
    }
}
