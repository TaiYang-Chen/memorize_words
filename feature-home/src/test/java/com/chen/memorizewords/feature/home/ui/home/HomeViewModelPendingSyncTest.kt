package com.chen.memorizewords.feature.home.ui.home

import com.chen.memorizewords.domain.sync.model.SyncBannerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelPendingSyncTest {

    @Test
    fun `canOpenPendingSyncDetails returns false when banner is hidden`() {
        assertFalse(canOpenPendingSyncDetails(SyncBannerState.Hidden))
    }

    @Test
    fun `canOpenPendingSyncDetails returns true when pending data exists`() {
        assertTrue(canOpenPendingSyncDetails(SyncBannerState.Pending(1)))
        assertTrue(canOpenPendingSyncDetails(SyncBannerState.Blocked(1)))
        assertTrue(canOpenPendingSyncDetails(SyncBannerState.Offline(1)))
    }
}
