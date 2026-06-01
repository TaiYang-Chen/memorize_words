package com.chen.memorizewords.domain.wordbook.service
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadCommandResult
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import com.chen.memorizewords.domain.wordbook.model.shop.ShopBooksQuery
import com.chen.memorizewords.domain.wordbook.repository.shop.RemoteWordBookRepository
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
        forceRefresh: Boolean = false,
        runInForeground: Boolean = true
    ): DownloadCommandResult = remoteWordBookRepository.downloadBook(
        book = book,
        forceRefresh = forceRefresh,
        runInForeground = runInForeground
    )

    suspend fun cancelDownload(bookId: Long) {
        remoteWordBookRepository.cancelDownload(bookId)
    }
}
