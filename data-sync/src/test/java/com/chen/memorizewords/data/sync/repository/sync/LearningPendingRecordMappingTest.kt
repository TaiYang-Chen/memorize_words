package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class LearningPendingRecordMappingTest {

    @Test
    fun `learning retry record is exposed through unified pending record model`() {
        val record = mapLearningPendingRecords(
            listOf(
                LearningOutboxEntity(
                    clientEventId = "event-1",
                    bookId = 7L,
                    wordId = 9L,
                    payload = "{\"clientEventId\":\"event-1\"}",
                    status = LearningOutboxEntity.STATUS_PENDING,
                    attemptCount = 2,
                    lastError = "RETRY|io|connection refused",
                    lastAttemptAt = 100L,
                    nextRetryAt = 200L,
                    createdAtMs = 50L,
                    updatedAtMs = 100L
                )
            )
        ).single()

        assertEquals("learning:event-1", record.id)
        assertEquals("learning", record.sourceId)
        assertEquals("LEARNING_RECORDED", record.bizType)
        assertEquals(SyncOutboxState.RETRY_WAITING.name, record.state)
        assertEquals(SyncOutboxFailureKind.NETWORK.name, record.failureKind)
        assertEquals(2, record.retryCount)
    }
}
