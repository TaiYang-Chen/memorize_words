package com.chen.memorizewords.data.wordbook.repository

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
                hasFailed = false,
                errorMessage = null
            ),
            isPaused = false
        )

        assertIs<DownloadState.Downloading>(state)
        assertEquals(50, state.progress)
    }
}
