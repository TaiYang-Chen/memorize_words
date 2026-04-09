package com.chen.memorizewords.feature.wordbook.shop

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.chen.memorizewords.core.common.paging.PageSlicePagingSource
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.service.wordbook.WordBookShopFacade
import com.chen.memorizewords.domain.model.wordbook.shop.DownloadState
import com.chen.memorizewords.domain.model.wordbook.shop.ShopBooksQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class BookShopViewModel @Inject constructor(
    private val wordBookShopFacade: WordBookShopFacade
) : BaseViewModel() {

    private val category = MutableStateFlow(DEFAULT_CATEGORY)
    private val keyword = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    private val queryFlow = combine(
        category,
        keyword.debounce(300)
    ) { currentCategory, currentKeyword ->
        currentCategory to currentKeyword.trim()
    }.distinctUntilChanged()

    private val downloadStateFlow = wordBookShopFacade.observeDownloadStates()

    val pagingData: Flow<PagingData<BookShopUi>> =
        queryFlow.flatMapLatest { (currentCategory, currentKeyword) ->
            Pager(
                config = PagingConfig(
                    pageSize = SHOP_PAGE_SIZE,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = {
                    PageSlicePagingSource { pageIndex, pageSize ->
                        wordBookShopFacade.getShopBooks(
                            ShopBooksQuery(
                                pageIndex = pageIndex,
                                pageSize = pageSize,
                                category = currentCategory,
                                keyword = currentKeyword
                            )
                        )
                    }
                }
            ).flow
        }.cachedIn(viewModelScope)
            .combine(downloadStateFlow) { paging, stateMap ->
                paging.map { book ->
                    val state = stateMap[book.id] ?: DownloadState.NotDownloaded
                    BookShopUi(book, state)
                }
            }

    fun setCategory(value: String) {
        category.value = value
    }

    fun setKeyword(value: String) {
        keyword.value = value
    }

    fun onDownload(book: BookShopUi) {
        viewModelScope.launch {
            val forceRefresh = book.downloadState is DownloadState.UpdateAvailable
            val result = wordBookShopFacade.downloadBook(
                book.book,
                forceRefresh = forceRefresh
            )
            showToast(result.message)
        }
    }

    fun onCancelDownload(bookId: Long) {
        viewModelScope.launch {
            wordBookShopFacade.cancelDownload(bookId)
        }
    }

    private companion object {
        const val DEFAULT_CATEGORY = "全部"
        const val SHOP_PAGE_SIZE = 20
    }
}
