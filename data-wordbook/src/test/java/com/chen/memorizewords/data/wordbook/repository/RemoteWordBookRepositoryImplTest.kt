package com.chen.memorizewords.data.wordbook.repository

import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordBookDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteWordBookRepositoryImplTest {

    @Test
    fun `toShopDomain preserves remote content version for download target`() {
        val dto = WordBookDto(
            id = 7L,
            title = "CET-4",
            category = "考试",
            imgUrl = "",
            description = "desc",
            totalWords = 1200,
            learnedWords = 0,
            contentVersion = 42L,
            updatedAt = 99L,
            isNew = true,
            isHot = false,
            isSelected = false,
            isPublic = true,
            createdByUserId = null
        )

        val book = dto.toShopDomain()

        assertEquals(42L, book.contentVersion)
    }

    @Test
    fun `resolveShopDownloadState returns downloaded for fully downloaded book`() {
        val state = resolveShopDownloadState(
            downloadedCount = 100,
            totalWords = 100,
            workState = null,
            isPaused = false
        )

        assertTrue(state is DownloadState.Downloaded)
    }

    @Test
    fun `resolveShopDownloadState keeps partial download paused`() {
        val state = resolveShopDownloadState(
            downloadedCount = 30,
            totalWords = 100,
            workState = null,
            isPaused = false
        )

        assertEquals(30, (state as DownloadState.Paused).progress)
    }

    @Test
    fun `resolveShopTargetVersion uses book content version`() {
        val book = WordBook(
            id = 9L,
            title = "TOEFL",
            category = "考试",
            imgUrl = "",
            description = "desc",
            totalWords = 500,
            contentVersion = 123L,
            isPublic = true,
            createdByUserId = null
        )

        assertEquals(123L, resolveShopTargetVersion(book))
    }
}
