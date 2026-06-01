package com.chen.memorizewords.feature.wordbook.shop

import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BookShopUiTest {

    @Test
    fun `update available is rendered as downloaded in shop`() {
        val ui = BookShopUi(
            book = testBook(),
            downloadState = DownloadState.UpdateAvailable
        )

        assertEquals(100, ui.progress)
        assertEquals("已下载", ui.actionText)
        assertFalse(ui.actionEnabled)
    }

    @Test
    fun `downloaded keeps disabled action in shop`() {
        val ui = BookShopUi(
            book = testBook(),
            downloadState = DownloadState.Downloaded
        )

        assertEquals("已下载", ui.actionText)
        assertFalse(ui.actionEnabled)
    }

    private fun testBook(): WordBook {
        return WordBook(
            id = 1L,
            title = "CET-4",
            category = "考试",
            imgUrl = "",
            description = "desc",
            totalWords = 100,
            isPublic = true,
            createdByUserId = null
        )
    }
}
