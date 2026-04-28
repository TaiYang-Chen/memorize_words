package com.chen.memorizewords.data.repository.sync

import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxEntity
import com.chen.memorizewords.domain.model.sync.SyncPendingRecord
import com.chen.memorizewords.domain.model.sync.SyncBannerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositoryImplTest {

    @Test
    fun `resolveSyncBannerState hides banner when there is no pending data`() {
        val state = resolveSyncBannerState(
            pendingCount = 0,
            retryableCount = 0,
            blockedCount = 0,
            hasNetwork = true
        )

        assertTrue(state is SyncBannerState.Hidden)
    }

    @Test
    fun `resolveSyncBannerState shows offline banner whenever pending data exists offline`() {
        val state = resolveSyncBannerState(
            pendingCount = 3,
            retryableCount = 2,
            blockedCount = 1,
            hasNetwork = false
        )

        assertEquals(SyncBannerState.Offline(3), state)
    }

    @Test
    fun `resolveSyncBannerState prefers pending message when retryable items exist online`() {
        val state = resolveSyncBannerState(
            pendingCount = 4,
            retryableCount = 2,
            blockedCount = 2,
            hasNetwork = true
        )

        assertEquals(SyncBannerState.Pending(4), state)
    }

    @Test
    fun `resolveSyncBannerState shows blocked message when only blocked items remain online`() {
        val state = resolveSyncBannerState(
            pendingCount = 2,
            retryableCount = 0,
            blockedCount = 2,
            hasNetwork = true
        )

        assertEquals(SyncBannerState.Blocked(2), state)
    }

    @Test
    fun `mapSyncPendingRecords returns empty list when no pending data exists`() {
        assertTrue(mapSyncPendingRecords(emptyList()).isEmpty())
    }

    @Test
    fun `mapSyncPendingRecords sorts by state priority then updatedAt descending`() {
        val queuedNewest = testEntity(
            id = 1L,
            state = SyncOutboxState.QUEUED,
            updatedAt = 300L
        )
        val blockedOldest = testEntity(
            id = 2L,
            state = SyncOutboxState.BLOCKED,
            updatedAt = 100L
        )
        val retryMid = testEntity(
            id = 3L,
            state = SyncOutboxState.RETRY_WAITING,
            updatedAt = 200L
        )
        val inFlightNewest = testEntity(
            id = 4L,
            state = SyncOutboxState.IN_FLIGHT,
            updatedAt = 400L
        )

        val records = mapSyncPendingRecords(
            listOf(queuedNewest, blockedOldest, retryMid, inFlightNewest)
        )

        assertEquals(listOf(2L, 3L, 4L, 1L), records.map(SyncPendingRecord::id))
    }

    @Test
    fun `mapSyncPendingRecords maps entity fields completely`() {
        val entity = testEntity(
            id = 7L,
            bizType = "PRACTICE_SESSION",
            bizKey = "practice_7",
            operation = SyncOutboxOperation.DELETE,
            payload = "{\"id\":7}",
            state = SyncOutboxState.BLOCKED,
            retryCount = 2,
            lastError = "bad request",
            failureKind = SyncOutboxFailureKind.CLIENT,
            lastAttemptAt = 900L,
            nextRetryAt = 1_200L,
            updatedAt = 1_500L
        )

        val record = mapSyncPendingRecords(listOf(entity)).single()

        assertEquals(
            SyncPendingRecord(
                id = 7L,
                bizType = "PRACTICE_SESSION",
                bizKey = "practice_7",
                operation = "DELETE",
                payload = "{\"id\":7}",
                state = "BLOCKED",
                retryCount = 2,
                lastError = "bad request",
                failureKind = "CLIENT",
                lastAttemptAt = 900L,
                nextRetryAt = 1_200L,
                updatedAt = 1_500L
            ),
            record
        )
    }

    private fun testEntity(
        id: Long,
        bizType: String = "STUDY_PLAN",
        bizKey: String = "key_$id",
        operation: SyncOutboxOperation = SyncOutboxOperation.UPSERT,
        payload: String = "{\"value\":$id}",
        state: SyncOutboxState = SyncOutboxState.QUEUED,
        retryCount: Int = 0,
        lastError: String? = null,
        failureKind: SyncOutboxFailureKind? = null,
        lastAttemptAt: Long = 0L,
        nextRetryAt: Long = 0L,
        updatedAt: Long
    ): SyncOutboxEntity {
        return SyncOutboxEntity(
            id = id,
            bizType = bizType,
            bizKey = bizKey,
            operation = operation,
            payload = payload,
            state = state,
            retryCount = retryCount,
            lastError = lastError,
            failureKind = failureKind,
            lastAttemptAt = lastAttemptAt,
            nextRetryAt = nextRetryAt,
            leaseToken = null,
            leaseExpiresAt = 0L,
            updatedAt = updatedAt
        )
    }
}
