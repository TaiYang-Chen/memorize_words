package com.chen.memorizewords.feature.wordbook.wordlist

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.chen.memorizewords.core.common.paging.PageSlicePagingSource
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.domain.study.usecase.word.study.ToggleFavoriteUseCase
import com.chen.memorizewords.domain.word.model.WordListRow
import com.chen.memorizewords.domain.word.model.enums.WordFilter
import com.chen.memorizewords.domain.word.model.enums.WordLearningStatus
import com.chen.memorizewords.domain.word.model.enums.WordSortType
import com.chen.memorizewords.domain.word.repository.WordRepository
import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.wordbook.model.WordListSummary
import com.chen.memorizewords.domain.wordbook.usecase.GetBookNameByIdUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetWordBookWordRowsPageUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetWordListSummaryUseCase
import com.chen.memorizewords.feature.wordbook.R
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WordListViewModel @Inject constructor(
    private val getWordBookWordRowsPageUseCase: GetWordBookWordRowsPageUseCase,
    private val getWordListSummaryUseCase: GetWordListSummaryUseCase,
    private val getBookNameByIdUseCase: GetBookNameByIdUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val wordRepository: WordRepository,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private val bookId = MutableStateFlow<Long?>(null)
    private val filter = MutableStateFlow(WordFilter.ALL)
    private val sortType = MutableStateFlow(WordSortType.BOOK_ORDER)
    private val keyword = MutableStateFlow("")
    private val refreshVersion = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(WordListUiState())
    val uiState: StateFlow<WordListUiState> = _uiState.asStateFlow()

    val pagingData: StateFlow<PagingData<WordListRow>> =
        combine(
            bookId.filterNotNull(),
            filter,
            sortType,
            keyword,
            refreshVersion
        ) { currentBookId, currentFilter, currentSort, currentKeyword, _ ->
            WordListQuery(
                wordBookId = currentBookId,
                pageIndex = 0,
                pageSize = WORD_LIST_PAGE_SIZE,
                filter = currentFilter,
                keyword = currentKeyword,
                sortType = currentSort,
                now = System.currentTimeMillis()
            )
        }.flatMapLatest { baseQuery ->
            updateUiStateForQuery(baseQuery)
            Pager(
                config = PagingConfig(
                    pageSize = WORD_LIST_PAGE_SIZE,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = {
                    PageSlicePagingSource { pageIndex, pageSize ->
                        getWordBookWordRowsPageUseCase(
                            baseQuery.copy(pageIndex = pageIndex, pageSize = pageSize)
                        )
                    }
                }
            ).flow.cachedIn(viewModelScope)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PagingData.empty())

    fun loadData(bookId: Long) {
        this.bookId.value = bookId
        viewModelScope.launch {
            val title = runCatching { getBookNameByIdUseCase(bookId) }
                .getOrElse { resourceProvider.getString(R.string.module_wordbook_unknown_book) }
                ?: resourceProvider.getString(R.string.module_wordbook_unknown_book)
            _uiState.update { it.copy(bookName = title) }
            refreshSummary()
        }
    }

    fun setFilter(nextFilter: WordFilter) {
        filter.value = nextFilter
    }

    fun setSortType(nextSortType: WordSortType) {
        sortType.value = nextSortType
    }

    fun setKeyword(nextKeyword: String) {
        keyword.value = nextKeyword
    }

    fun clearKeyword() {
        keyword.value = ""
    }

    fun toggleFavorite(row: WordListRow) {
        viewModelScope.launch {
            val word = wordRepository.getWordById(row.wordId)
            if (word == null) {
                showToast("单词信息不可用")
                return@launch
            }
            runCatching { toggleFavoriteUseCase(word) }
                .onSuccess {
                    showToast(if (row.isFavorite) "已取消重点词" else "已加入重点词")
                    refreshVersion.update { it + 1 }
                    refreshSummary()
                    emitEffect(WordListEffect.RefreshList)
                }
                .onFailure {
                    showToast("重点词更新失败，请稍后再试")
                }
        }
    }

    fun openWord(row: WordListRow) {
        emitEffect(WordListEffect.OpenWord(row.wordId))
    }

    fun playPronunciation(row: WordListRow) {
        showToast("发音能力待接入")
    }

    private suspend fun updateUiStateForQuery(query: WordListQuery) {
        _uiState.update {
            it.copy(
                selectedFilter = query.filter,
                selectedSortType = query.sortType,
                keyword = query.normalizedKeyword,
                showFastIndex = query.normalizedKeyword.isEmpty() &&
                    query.sortType == WordSortType.BOOK_ORDER &&
                    query.filter != WordFilter.REVIEW_DUE
            )
        }
        refreshSummary(query.wordBookId, query.now)
    }

    private fun refreshSummary() {
        val currentBookId = bookId.value ?: return
        viewModelScope.launch {
            refreshSummary(currentBookId, System.currentTimeMillis())
        }
    }

    private suspend fun refreshSummary(wordBookId: Long, now: Long) {
        val summary = runCatching { getWordListSummaryUseCase(wordBookId, now) }
            .getOrDefault(WordListSummary())
        _uiState.update {
            it.copy(
                summary = summary,
                summaryText = "共 ${summary.totalCount} 词 · 已学 ${summary.learnedCount} · 待复习 ${summary.reviewDueCount}"
            )
        }
    }

    private companion object {
        const val WORD_LIST_PAGE_SIZE = 30
    }
}

data class WordListUiState(
    val bookName: String = "",
    val summary: WordListSummary = WordListSummary(),
    val summaryText: String = "共 0 词 · 已学 0 · 待复习 0",
    val selectedFilter: WordFilter = WordFilter.ALL,
    val selectedSortType: WordSortType = WordSortType.BOOK_ORDER,
    val keyword: String = "",
    val showFastIndex: Boolean = true
)

sealed interface WordListEffect : UiEffect {
    data class OpenWord(val wordId: Long) : WordListEffect
    data object RefreshList : WordListEffect
}

fun WordLearningStatus.displayName(): String {
    return when (this) {
        WordLearningStatus.TO_LEARN -> "未学"
        WordLearningStatus.LEARNED -> "已学"
        WordLearningStatus.MASTERED -> "掌握"
        WordLearningStatus.REVIEW_DUE -> "待复习"
    }
}
