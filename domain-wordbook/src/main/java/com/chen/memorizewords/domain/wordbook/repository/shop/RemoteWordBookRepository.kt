package com.chen.memorizewords.domain.wordbook.repository.shop
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadCommandResult
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import com.chen.memorizewords.domain.wordbook.model.shop.ShopBooksQuery
import kotlinx.coroutines.flow.Flow

interface RemoteWordBookRepository {
    suspend fun getShopBooks(query: ShopBooksQuery): PageSlice<WordBook>
    fun observeDownloadStates(): Flow<Map<Long, DownloadState>>
    suspend fun downloadBook(
        book: WordBook,
        forceRefresh: Boolean = false,
        runInForeground: Boolean = true
    ): DownloadCommandResult
    suspend fun cancelDownload(bookId: Long)
}
