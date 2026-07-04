package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.domain.sync.model.SyncBannerState
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncBannerStateResolverTest {
    @Test
    fun `unauthenticated user never sees pending sync banner`() {
        val state = resolveSyncBannerState(
            isAuthenticated = false,
            pendingCount = 7,
            retryableCount = 7,
            blockedCount = 0,
            hasNetwork = false
        )

        assertEquals(SyncBannerState.Hidden, state)
    }

    @Test
    fun `authenticated offline user sees offline banner for current pending data`() {
        val state = resolveSyncBannerState(
            isAuthenticated = true,
            pendingCount = 7,
            retryableCount = 7,
            blockedCount = 0,
            hasNetwork = false
        )

        assertEquals(SyncBannerState.Offline(7), state)
    }

    @Test
    fun `authenticated user without pending data sees no banner`() {
        val state = resolveSyncBannerState(
            isAuthenticated = true,
            pendingCount = 0,
            retryableCount = 0,
            blockedCount = 0,
            hasNetwork = true
        )

        assertEquals(SyncBannerState.Hidden, state)
    }
}
