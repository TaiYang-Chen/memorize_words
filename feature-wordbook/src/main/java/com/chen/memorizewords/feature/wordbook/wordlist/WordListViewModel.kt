package com.chen.memorizewords.feature.wordbook.wordlist

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.chen.memorizewords.core.common.paging.PageSlicePagingSource
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.domain.model.wordbook.WordListQuery
import com.chen.memorizewords.domain.model.words.WordListRow
import com.chen.memorizewords.domain.model.words.enums.WordFilter
import com.chen.memorizewords.domain.usecase.wordbook.GetBookNameByIdUseCase
import com.chen.memorizewords.domain.usecase.wordbook.GetWordBookWordRowsPageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WordListViewModel @Inject constructor(
    private val getWordBookWordRowsPageUseCase: GetWordBookWordRowsPageUseCase,
    private val getBookNameByIdUseCase: GetBookNameByIdUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private val filter = MutableStateFlow(WordFilter.ALL)
    private val bookId = MutableStateFlow<Long?>(null)

    private val _bookName = MutableStateFlow<String?>(null)
    val bookName: StateFlow<String?> = _bookName.asStateFlow()

    val pagingData: StateFlow<PagingData<WordListRow>> =
        combine(bookId.filterNotNull(), filter) { currentBookId, currentFilter ->
            currentBookId to currentFilter
        }.flatMapLatest { (currentBookId, currentFilter) ->
            Pager(
                config = PagingConfig(
                    pageSize = WORD_LIST_PAGE_SIZE,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = {
                    PageSlicePagingSource { pageIndex, pageSize ->
                        getWordBookWordRowsPageUseCase(
                            WordListQuery(
                                wordBookId = currentBookId,
                                pageIndex = pageIndex,
                                pageSize = pageSize,
                                filter = currentFilter
                            )
                        )
                    }
                }
            ).flow.cachedIn(viewModelScope)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PagingData.empty())

    fun setFilter(filter: WordFilter) {
        this.filter.value = filter
    }

    fun loadData(bookId: Long) {
        this.bookId.value = bookId
        loadBookName(bookId)
    }

    private fun loadBookName(bookId: Long) {
        viewModelScope.launch {
            _bookName.value = runCatching { getBookNameByIdUseCase(bookId) }
                .getOrElse { resourceProvider.getString(R.string.module_wordbook_unknown_book) }
        }
    }

    private companion object {
        const val WORD_LIST_PAGE_SIZE = 30
    }
}
