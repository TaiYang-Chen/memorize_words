package com.chen.memorizewords.feature.onboarding

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.chen.memorizewords.core.common.paging.PageSlicePagingSource
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.wordbook.model.WordBook
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

@HiltViewModel
class OnboardingSelectWordBookViewModel @Inject constructor(
    private val wordBookShopFacade: WordBookShopFacade
) : BaseViewModel() {

    private val category = MutableStateFlow(DEFAULT_ONBOARDING_WORD_BOOK_CATEGORY)
    private val searchState = MutableStateFlow(OnboardingSearchState())

    val currentSearchState = searchState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagingData: Flow<PagingData<WordBook>> =
        combine(category, searchState) { currentCategory, currentSearchState ->
            OnboardingWordBookQueryState(
                category = currentCategory,
                searchState = currentSearchState
            )
        }.distinctUntilChanged()
            .flatMapLatest { queryState ->
                val isSearchMode = queryState.searchState.isSearchMode
                val trimmedKeyword = queryState.searchState.keyword.trim()
                val effectiveCategory = if (isSearchMode) {
                    DEFAULT_ONBOARDING_WORD_BOOK_CATEGORY
                } else {
                    queryState.category
                }
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
                                    pageSize = PAGE_SIZE,
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
            }
            .cachedIn(viewModelScope)

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
        searchState.value = OnboardingSearchState()
    }
}

data class OnboardingSearchState(
    val isSearchMode: Boolean = false,
    val keyword: String = ""
)

private data class OnboardingWordBookQueryState(
    val category: String,
    val searchState: OnboardingSearchState
)

private const val PAGE_SIZE = 20
private const val SEARCH_DEBOUNCE_MS = 300L
