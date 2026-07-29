package com.chen.memorizewords.feature.learning.ui.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.practice.model.ExamCategory
import com.chen.memorizewords.domain.practice.model.ExamPracticeWord
import com.chen.memorizewords.domain.practice.model.ExamQuestionType
import com.chen.memorizewords.domain.practice.model.WordExamItem
import com.chen.memorizewords.domain.practice.repository.ExamPracticeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ARG_WORD_ID = "wordId"
private const val ARG_WORD_TEXT = "wordText"

data class WordExamPracticeItemUi(
    val item: WordExamItem,
    val showAnswer: Boolean = false
)

data class WordExamPracticeUiState(
    val isLoading: Boolean = true,
    val wordId: Long = -1L,
    val wordText: String = "",
    val items: List<WordExamPracticeItemUi> = emptyList(),
    val visibleItems: List<WordExamPracticeItemUi> = emptyList(),
    val selectedTypes: Set<ExamQuestionType> = emptySet(),
    val selectedCategory: ExamCategory? = null,
    val totalCount: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class WordExamPracticeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val examPracticeRepository: ExamPracticeRepository
) : BaseViewModel() {

    private val wordId: Long = savedStateHandle.get<Long>(ARG_WORD_ID) ?: -1L
    private val fallbackWordText: String = savedStateHandle.get<String>(ARG_WORD_TEXT).orEmpty()

    private val _uiState = MutableStateFlow(
        WordExamPracticeUiState(
            wordId = wordId,
            wordText = fallbackWordText
        )
    )
    val uiState: StateFlow<WordExamPracticeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (wordId <= 0L) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "\u65e0\u6548\u5355\u8bcd") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            examPracticeRepository.getWordPractice(wordId)
                .onSuccess { practice ->
                    _uiState.update { previous -> buildStateFromWord(previous, practice) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "\u771f\u9898\u52a0\u8f7d\u5931\u8d25"
                        )
                    }
                }
        }
    }

    fun toggleType(type: ExamQuestionType) {
        _uiState.update { state ->
            val nextState = state.copy(selectedTypes = state.selectedTypes.toggle(type))
            nextState.copy(visibleItems = applyFilters(nextState))
        }
    }

    fun clearTypeFilters() {
        _uiState.update { state ->
            val nextState = state.copy(selectedTypes = emptySet())
            nextState.copy(visibleItems = applyFilters(nextState))
        }
    }

    fun setCategory(category: ExamCategory?) {
        _uiState.update { state ->
            val nextState = state.copy(selectedCategory = category)
            nextState.copy(visibleItems = applyFilters(nextState))
        }
    }

    fun showVisibleAnswers() {
        updateVisibleAnswers(showAnswer = true)
    }

    fun hideVisibleAnswers() {
        updateVisibleAnswers(showAnswer = false)
    }

    fun toggleAnswer(itemId: Long) {
        updateItems { itemUi ->
            if (itemUi.item.id != itemId || itemUi.item.questionType == ExamQuestionType.PASSAGE) {
                itemUi
            } else {
                itemUi.copy(showAnswer = !itemUi.showAnswer)
            }
        }
    }

    private fun buildStateFromWord(
        previous: WordExamPracticeUiState,
        practice: ExamPracticeWord
    ): WordExamPracticeUiState {
        val items = practice.examItems.map { item ->
            previous.items.firstOrNull { it.item.id == item.id }?.copy(item = item)
                ?: WordExamPracticeItemUi(item = item)
        }
        val base = previous.copy(
            isLoading = false,
            wordId = practice.wordId,
            wordText = practice.word.ifBlank { previous.wordText },
            items = items,
            totalCount = practice.totalCount.takeIf { it > 0 } ?: items.size,
            errorMessage = null
        )
        return base.copy(visibleItems = applyFilters(base))
    }

    private fun updateItems(transform: (WordExamPracticeItemUi) -> WordExamPracticeItemUi) {
        _uiState.update { state ->
            val nextState = state.copy(items = state.items.map(transform))
            nextState.copy(visibleItems = applyFilters(nextState))
        }
    }

    private fun updateVisibleAnswers(showAnswer: Boolean) {
        val visibleItemIds = uiState.value.visibleItems
            .asSequence()
            .filterNot { it.item.questionType == ExamQuestionType.PASSAGE }
            .map { it.item.id }
            .toSet()
        if (visibleItemIds.isEmpty()) return

        updateItems { itemUi ->
            if (itemUi.item.id in visibleItemIds) {
                itemUi.copy(showAnswer = showAnswer)
            } else {
                itemUi
            }
        }
    }

    private fun applyFilters(state: WordExamPracticeUiState): List<WordExamPracticeItemUi> {
        val sorted = state.items.sortedWith(compareBy({ it.item.sortOrder }, { it.item.id }))
        val showingPassageOnly = state.selectedTypes == setOf(ExamQuestionType.PASSAGE)
        if (showingPassageOnly) {
            return sorted.filter { itemUi ->
                itemUi.item.questionType == ExamQuestionType.PASSAGE &&
                    matchesCategory(itemUi, state.selectedCategory)
            }
        }

        val answerableItems = sorted.filter { itemUi ->
            itemUi.item.questionType != ExamQuestionType.PASSAGE &&
                matchesType(itemUi, state.selectedTypes) &&
                matchesCategory(itemUi, state.selectedCategory)
        }
        val answerableIds = answerableItems.mapTo(mutableSetOf()) { it.item.id }
        val groupKeys = answerableItems.mapNotNullTo(mutableSetOf()) { it.item.groupKey }

        return sorted.filter { itemUi ->
            if (itemUi.item.questionType == ExamQuestionType.PASSAGE) {
                !itemUi.item.groupKey.isNullOrBlank() && itemUi.item.groupKey in groupKeys
            } else {
                itemUi.item.id in answerableIds
            }
        }
    }

    private fun matchesType(
        itemUi: WordExamPracticeItemUi,
        selectedTypes: Set<ExamQuestionType>
    ): Boolean {
        return selectedTypes.isEmpty() || itemUi.item.questionType in selectedTypes
    }

    private fun matchesCategory(
        itemUi: WordExamPracticeItemUi,
        selectedCategory: ExamCategory?
    ): Boolean {
        return selectedCategory == null || itemUi.item.examCategory == selectedCategory
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> {
    return if (contains(value)) this - value else this + value
}
