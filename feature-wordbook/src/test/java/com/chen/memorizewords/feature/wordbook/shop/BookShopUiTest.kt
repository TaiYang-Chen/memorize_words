package com.chen.memorizewords.feature.wordbook.shop

import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BookShopUiTest {

    @Test
    fun `downloading with zero progress does not show retry`() {
        val item = BookShopUi(
            book = testBook(),
            downloadState = DownloadState.Downloading(progress = 0)
        )

        assertEquals("下载中", item.actionText)
        assertNotEquals("重试", item.actionText)
    }

    @Test
    fun `failed state shows retry`() {
        val item = BookShopUi(
            book = testBook(),
            downloadState = DownloadState.Failed(message = "network failed")
        )

        assertEquals("重试", item.actionText)
    }

    private fun testBook(): WordBook {
        return WordBook(
            id = 1L,
            title = "test",
            category = "test",
            imgUrl = "",
            description = "",
            totalWords = 100,
            isPublic = true,
            createdByUserId = null
        )
    }
}
