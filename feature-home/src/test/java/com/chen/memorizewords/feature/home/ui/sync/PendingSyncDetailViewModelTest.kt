package com.chen.memorizewords.feature.home.ui.sync

import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import com.chen.memorizewords.domain.sync.model.SyncBannerState
import com.chen.memorizewords.domain.sync.model.SyncPendingRecord
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import com.chen.memorizewords.domain.sync.service.SyncFacade
import com.chen.memorizewords.feature.home.MainDispatcherRule
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingSyncDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `uiState shows empty state when no pending records exist`() = runTest {
        val repository = FakeSyncRepository()
        val viewModel = PendingSyncDetailViewModel(
            syncFacade = SyncFacade(repository),
            formatter = PendingSyncDetailFormatter(Gson())
        )

        backgroundScope.launch {
            viewModel.uiState.collect { }
        }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun `uiState maps pending records and toggles expanded item`() = runTest {
        val repository = FakeSyncRepository()
        val viewModel = PendingSyncDetailViewModel(
            syncFacade = SyncFacade(repository),
            formatter = PendingSyncDetailFormatter(Gson())
        )
        val record = SyncPendingRecord(
            id = 11L,
            bizType = "STUDY_PLAN",
            bizKey = "study_plan",
            operation = "UPSERT",
            payload = """{"dailyNewWords":10,"dailyReviewWords":20,"testMode":"MEANING","wordOrderType":"RANDOM"}""",
            state = "QUEUED",
            retryCount = 0,
            lastError = null,
            failureKind = null,
            lastAttemptAt = 0L,
            nextRetryAt = 0L,
            updatedAt = 1714000000000L
        )

        backgroundScope.launch {
            viewModel.uiState.collect { }
        }
        repository.pendingRecords.value = listOf(record)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEmpty)
        assertEquals(1, viewModel.uiState.value.items.size)
        assertFalse(viewModel.uiState.value.items.first().isExpanded)

        viewModel.onItemClicked(11L)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.items.first().isExpanded)
        assertTrue(
            viewModel.uiState.value.items.first().detailFields.any {
                it.label == "\u6bcf\u65e5\u65b0\u8bcd\u6570"
            }
        )
    }

    private class FakeSyncRepository : SyncRepository {
        val pendingRecords = MutableStateFlow<List<SyncPendingRecord>>(emptyList())

        override fun startPostLoginBootstrap() = Unit

        override fun getCurrentPostLoginBootstrapState(): PostLoginBootstrapState =
            PostLoginBootstrapState.Idle

        override fun scheduleBootstrapSync() = Unit

        override fun observePostLoginBootstrapState(): Flow<PostLoginBootstrapState> =
            emptyFlow()

        override fun observePendingSyncCount(): Flow<Int> = emptyFlow()

        override fun observePendingSyncRecords(): Flow<List<SyncPendingRecord>> = pendingRecords

        override fun observeSyncBannerState(): Flow<SyncBannerState> = emptyFlow()

        override fun triggerDrain() = Unit
    }
}
