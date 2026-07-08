package com.chen.memorizewords.data.sync.repository.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncOutboxStoreTest {
    @Test
    fun `batch enqueue preserves existing row id by biz key`() {
        val existing = SyncOutboxEntityFactory.entity(
            id = 42L,
            bizKey = "study_record:1",
            payload = "old"
        )

        val entities = buildQueuedOutboxEntities(
            commands = listOf(
                SyncOutboxWriteCommand(
                    bizType = SyncOutboxBizType.DAILY_STUDY_DURATION,
                    bizKey = "study_record:1",
                    operation = SyncOutboxOperation.UPSERT,
                    payload = "new",
                    updatedAt = 100L
                )
            ),
            existingByBizKey = mapOf(existing.bizKey to existing)
        )

        assertEquals(42L, entities.single().id)
        assertEquals("new", entities.single().payload)
        assertEquals(SyncOutboxState.QUEUED, entities.single().state)
        assertEquals(0, entities.single().retryCount)
    }
}

private object SyncOutboxEntityFactory {
    fun entity(
        id: Long,
        bizKey: String,
        payload: String
    ): com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxEntity {
        return com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxEntity(
            id = id,
            bizType = SyncOutboxBizType.DAILY_STUDY_DURATION,
            bizKey = bizKey,
            operation = SyncOutboxOperation.UPSERT,
            payload = payload,
            state = SyncOutboxState.RETRY_WAITING,
            retryCount = 3,
            lastError = "old error",
            failureKind = SyncOutboxFailureKind.NETWORK,
            lastAttemptAt = 10L,
            nextRetryAt = 20L,
            leaseToken = "lease",
            leaseExpiresAt = 30L,
            updatedAt = 40L
        )
    }
}
