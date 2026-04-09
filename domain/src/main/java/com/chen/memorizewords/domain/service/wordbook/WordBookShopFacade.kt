package com.chen.memorizewords.domain.service.wordbook

import com.chen.memorizewords.domain.model.common.PageSlice
import com.chen.memorizewords.domain.model.wordbook.WordBook
import com.chen.memorizewords.domain.model.wordbook.shop.DownloadCommandResult
import com.chen.memorizewords.domain.model.wordbook.shop.DownloadState
import com.chen.memorizewords.domain.model.wordbook.shop.ShopBooksQuery
import com.chen.memorizewords.domain.repository.shop.RemoteWordBookRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class WordBookShopFacade @Inject constructor(
    private val remoteWordBookRepository: RemoteWordBookRepository
) {
    suspend fun getShopBooks(query: ShopBooksQuery): PageSlice<WordBook> =
        remoteWordBookRepository.getShopBooks(query)

    fun observeDownloadStates(): Flow<Map<Long, DownloadState>> =
        remoteWordBookRepository.observeDownloadStates()

    suspend fun downloadBook(
        book: WordBook,
        forceRefresh: Boolean = false
    ): DownloadCommandResult = remoteWordBookRepository.downloadBook(book, forceRefresh)

    suspend fun cancelDownload(bookId: Long) {
        remoteWordBookRepository.cancelDownload(bookId)
    }
}
