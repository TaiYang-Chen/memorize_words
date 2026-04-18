package com.chen.memorizewords.data.repository.sync

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
}
