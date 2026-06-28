package com.chen.memorizewords.data.wordbook.repository

import androidx.work.WorkInfo
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RemoteWordBookRepositoryImplTest {

    @Test
    fun `active work keeps download state even when progress is zero`() {
        val state = resolveShopDownloadState(
            downloadedCount = 0,
            totalWords = 100,
            workState = BookWorkState(
                isActive = true,
                progress = 0,
                isSucceeded = false,
                hasFailed = false,
                errorMessage = null
            ),
            isPaused = false
        )

        assertIs<DownloadState.Downloading>(state)
        assertEquals(0, state.progress)
    }

    @Test
    fun `downloaded word count wins over stale lower work progress`() {
        val state = resolveShopDownloadState(
            downloadedCount = 50,
            totalWords = 100,
            workState = BookWorkState(
                isActive = true,
                progress = 0,
                isSucceeded = false,
                hasFailed = false,
                errorMessage = null
            ),
            isPaused = false
        )

        assertIs<DownloadState.Downloading>(state)
        assertEquals(50, state.progress)
    }

    @Test
    fun `active work wins over older failed work`() {
        val workState = resolveBookWorkState(
            listOf(
                BookWorkSnapshot(
                    state = WorkInfo.State.FAILED,
                    errorMessage = "old failure"
                ),
                BookWorkSnapshot(
                    state = WorkInfo.State.ENQUEUED,
                    progress = 0
                )
            )
        )

        val state = resolveShopDownloadState(
            downloadedCount = 0,
            totalWords = 100,
            workState = workState,
            isPaused = false
        )

        assertIs<DownloadState.Downloading>(state)
        assertEquals(0, state.progress)
    }

    @Test
    fun `cancelled failed work is ignored`() {
        val workState = resolveBookWorkState(
            listOf(
                BookWorkSnapshot(
                    state = WorkInfo.State.FAILED,
                    isCancelled = true,
                    errorMessage = "Download cancelled"
                )
            )
        )

        val state = resolveShopDownloadState(
            downloadedCount = 0,
            totalWords = 100,
            workState = workState,
            isPaused = false
        )

        assertIs<DownloadState.NotDownloaded>(state)
    }

    @Test
    fun `real failed work shows failed when no active work exists`() {
        val workState = resolveBookWorkState(
            listOf(
                BookWorkSnapshot(
                    state = WorkInfo.State.FAILED,
                    errorMessage = "network failed"
                )
            )
        )

        val state = resolveShopDownloadState(
            downloadedCount = 0,
            totalWords = 100,
            workState = workState,
            isPaused = false
        )

        val failed = assertIs<DownloadState.Failed>(state)
        assertEquals("network failed", failed.message)
    }

    @Test
    fun `complete local word count wins over old failed work`() {
        val state = resolveShopDownloadState(
            downloadedCount = 100,
            totalWords = 100,
            workState = BookWorkState(
                isActive = false,
                progress = 0,
                isSucceeded = false,
                hasFailed = true,
                errorMessage = "old failure"
            ),
            isPaused = false
        )

        assertIs<DownloadState.Downloaded>(state)
    }
}
