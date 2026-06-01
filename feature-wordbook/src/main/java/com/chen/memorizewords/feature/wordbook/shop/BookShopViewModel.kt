package com.chen.memorizewords.feature.wordbook.shop

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.chen.memorizewords.core.common.paging.PageSlicePagingSource
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import com.chen.memorizewords.domain.wordbook.model.shop.ShopBooksQuery
import com.chen.memorizewords.domain.wordbook.service.WordBookShopFacade
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class BookShopViewModel @Inject constructor(
    private val wordBookShopFacade: WordBookShopFacade
) : BaseViewModel() {

    private val category = MutableStateFlow(DEFAULT_CATEGORY)
    private val searchState = MutableStateFlow(BookShopSearchState())

    val currentSearchState = searchState.asStateFlow()

    private val queryFlow = combine(
        category,
        searchState
    ) { currentCategory, currentSearchState ->
        BookShopQueryState(
            category = currentCategory,
            searchState = currentSearchState
        )
    }.distinctUntilChanged()

    private val downloadStateFlow = wordBookShopFacade.observeDownloadStates()

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagingData: Flow<PagingData<BookShopUi>> =
        queryFlow.flatMapLatest { queryState ->
            val isSearchMode = queryState.searchState.isSearchMode
            val trimmedKeyword = queryState.searchState.keyword.trim()
            val effectiveCategory = if (isSearchMode) DEFAULT_CATEGORY else queryState.category
            val effectiveKeyword = if (isSearchMode) trimmedKeyword else ""

            if (isSearchMode && effectiveKeyword.isBlank()) {
                flowOf(PagingData.empty())
            } else {
                flow {
                    if (isSearchMode) {
                        delay(SEARCH_DEBOUNCE_MS)
                    }
                    emitAll(
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
                                            category = effectiveCategory,
                                            keyword = effectiveKeyword
                                        )
                                    )
                                }
                            }
                        ).flow
                    )
                }
            }
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
        searchState.value = searchState.value.copy(keyword = value)
    }

    fun enterSearchMode() {
        if (searchState.value.isSearchMode) return
        searchState.value = searchState.value.copy(isSearchMode = true)
    }

    fun cancelSearch() {
        searchState.value = BookShopSearchState()
    }

    fun onDownload(book: BookShopUi) {
        viewModelScope.launch {
            val result = wordBookShopFacade.downloadBook(
                book.book
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
        const val DEFAULT_CATEGORY = "\u5168\u90E8"
        const val SHOP_PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

data class BookShopSearchState(
    val isSearchMode: Boolean = false,
    val keyword: String = ""
)

private data class BookShopQueryState(
    val category: String,
    val searchState: BookShopSearchState
)
