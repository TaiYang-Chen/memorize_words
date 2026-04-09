package com.chen.memorizewords.domain.repository.shop

import com.chen.memorizewords.domain.model.common.PageSlice
import com.chen.memorizewords.domain.model.wordbook.WordBook
import com.chen.memorizewords.domain.model.wordbook.shop.DownloadCommandResult
import com.chen.memorizewords.domain.model.wordbook.shop.DownloadState
import com.chen.memorizewords.domain.model.wordbook.shop.ShopBooksQuery
import kotlinx.coroutines.flow.Flow

interface RemoteWordBookRepository {
    suspend fun getShopBooks(query: ShopBooksQuery): PageSlice<WordBook>
    fun observeDownloadStates(): Flow<Map<Long, DownloadState>>
    suspend fun downloadBook(
        book: WordBook,
        forceRefresh: Boolean = false
    ): DownloadCommandResult
    suspend fun cancelDownload(bookId: Long)
}
