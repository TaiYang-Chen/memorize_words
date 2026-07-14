package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncDeliveryMode
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventEntity
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncState
import com.chen.memorizewords.domain.sync.FailureQueueEventType
import kotlin.test.Test
import kotlin.test.assertEquals

class LearningPendingRecordMappingTest {

    @Test
    fun `failed learning event is exposed through pending record model`() {
        val record = mapFailedSyncPendingRecords(
            listOf(
                FailedSyncEventEntity(
                    eventId = "event-1",
                    eventType = FailureQueueEventType.LEARNING_EVENT,
                    schemaVersion = 1,
                    deliveryMode = FailedSyncDeliveryMode.APPEND,
                    dedupeKey = null,
                    orderingKey = "learning:7",
                    sequence = 2L,
                    paramsJson = "{\"clientEventId\":\"event-1\"}",
                    state = FailedSyncState.RETRY_WAITING,
                    attemptCount = 2,
                    lastError = "connection refused",
                    nextAttemptAtMs = 200L,
                    leaseToken = null,
                    leaseExpiresAtMs = 0L,
                    occurredAtMs = 50L,
                    createdAtMs = 50L,
                    updatedAtMs = 100L
                )
            )
        ).single()

        assertEquals("failed:event-1", record.id)
        assertEquals("failed", record.sourceId)
        assertEquals(FailureQueueEventType.LEARNING_EVENT, record.bizType)
        assertEquals(FailedSyncState.RETRY_WAITING.name, record.state)
        assertEquals(SyncOutboxFailureKind.NETWORK.name, record.failureKind)
        assertEquals(2, record.retryCount)
    }
}
