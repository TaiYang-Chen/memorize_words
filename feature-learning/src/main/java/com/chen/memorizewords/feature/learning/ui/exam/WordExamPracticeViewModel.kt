package com.chen.memorizewords.feature.learning.ui.exam

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.practice.model.ExamCategory
import com.chen.memorizewords.domain.practice.model.ExamPracticeAnswerSubmission
import com.chen.memorizewords.domain.practice.model.ExamPracticeSessionSubmission
import com.chen.memorizewords.domain.practice.model.ExamPracticeWord
import com.chen.memorizewords.domain.practice.model.ExamQuestionType
import com.chen.memorizewords.domain.practice.model.WordExamItem
import com.chen.memorizewords.domain.practice.ExamPracticeSessionPolicy
import com.chen.memorizewords.domain.practice.ExamPracticeSessionSummary
import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeKind
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.PracticeReport
import com.chen.memorizewords.domain.practice.PracticeReportRepository
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.PracticeSessionReportRecord
import com.chen.memorizewords.domain.practice.repository.ExamPracticeRepository
import com.chen.memorizewords.domain.practice.service.PracticeFacade
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ARG_WORD_ID = "wordId"
private const val ARG_WORD_TEXT = "wordText"

enum class ExamStatusFilter {
    ALL,
    FAVORITE,
    WRONG
}

data class WordExamPracticeItemUi(
    val item: WordExamItem,
    val showAnswer: Boolean = false,
    val selectedOptionIndex: Int? = null,
    val selectedClozeAnswers: List<String> = emptyList(),
    val matchingPairs: Map<Int, Int> = emptyMap(),
    val pendingLeftIndex: Int? = null,
    val translationInput: String = "",
    val viewedAnswer: Boolean = false,
    val submitCount: Int = 0
)

data class WordExamPracticeUiState(
    val isLoading: Boolean = true,
    val wordId: Long = -1L,
    val wordText: String = "",
    val items: List<WordExamPracticeItemUi> = emptyList(),
    val visibleItems: List<WordExamPracticeItemUi> = emptyList(),
    val selectedTypes: Set<ExamQuestionType> = emptySet(),
    val selectedCategory: ExamCategory? = null,
    val statusFilter: ExamStatusFilter = ExamStatusFilter.ALL,
    val totalCount: Int = 0,
    val favoriteCount: Int = 0,
    val wrongCount: Int = 0,
    val objectiveCount: Int = 0,
    val isReadOnlyCache: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class WordExamPracticeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val examPracticeRepository: ExamPracticeRepository,
    private val practiceFacade: PracticeFacade,
    private val practiceReportRepository: PracticeReportRepository
) : BaseViewModel() {

    private val wordId: Long = savedStateHandle.get<Long>(ARG_WORD_ID) ?: -1L
    private val fallbackWordText: String = savedStateHandle.get<String>(ARG_WORD_TEXT).orEmpty()
    private val startedAtElapsed = SystemClock.elapsedRealtime()
    private val sessionId = "exam:${wordId}:${System.currentTimeMillis()}"
    private val sessionPolicy = ExamPracticeSessionPolicy()

    private val _uiState = MutableStateFlow(
        WordExamPracticeUiState(
            wordId = wordId,
            wordText = fallbackWordText
        )
    )
    val uiState: StateFlow<WordExamPracticeUiState> = _uiState.asStateFlow()

    private var finished = false

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
                    _uiState.update {
                        buildStateFromWord(
                            previous = it,
                            practice = practice
                        )
                    }
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
            val nextTypes = state.selectedTypes.toggle(type)
            val nextState = state.copy(selectedTypes = nextTypes)
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

    fun setStatusFilter(filter: ExamStatusFilter) {
        _uiState.update { state ->
            val nextState = state.copy(statusFilter = filter)
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
        updateItems { item ->
            if (item.item.id != itemId || item.item.questionType == ExamQuestionType.PASSAGE) {
                item
            } else {
                val showAnswer = !item.showAnswer
                item.copy(showAnswer = showAnswer, viewedAnswer = item.viewedAnswer || showAnswer)
            }
        }
    }

    fun selectSingleChoice(itemId: Long, index: Int) {
        updateItems { item ->
            if (item.item.id != itemId) item
            else item.copy(
                selectedOptionIndex = index,
                submitCount = (item.submitCount + 1).coerceAtLeast(1)
            )
        }
    }

    fun toggleClozeChoice(itemId: Long, answer: String) {
        updateItems { item ->
            if (item.item.id != itemId) {
                item
            } else {
                val nextAnswers = item.selectedClozeAnswers.toMutableList().apply {
                    if (contains(answer)) remove(answer) else add(answer)
                }
                item.copy(
                    selectedClozeAnswers = nextAnswers,
                    submitCount = (item.submitCount + 1).coerceAtLeast(1)
                )
            }
        }
    }

    fun selectMatchingLeft(itemId: Long, leftIndex: Int) {
        updateItems { item ->
            if (item.item.id != itemId) item else item.copy(pendingLeftIndex = leftIndex)
        }
    }

    fun selectMatchingRight(itemId: Long, rightIndex: Int) {
        updateItems { item ->
            if (item.item.id != itemId || item.pendingLeftIndex == null) {
                item
            } else {
                val nextPairs = item.matchingPairs.toMutableMap().apply {
                    put(item.pendingLeftIndex, rightIndex)
                }
                item.copy(
                    matchingPairs = nextPairs,
                    pendingLeftIndex = null,
                    submitCount = (item.submitCount + 1).coerceAtLeast(1)
                )
            }
        }
    }

    fun updateTranslation(itemId: Long, text: String) {
        updateItems { item ->
            if (item.item.id != itemId) item
            else item.copy(translationInput = text)
        }
    }

    fun toggleFavorite(itemId: Long) {
        val state = uiState.value
        if (state.isReadOnlyCache) {
            return
        }
        val currentFavorite = state.items.firstOrNull { it.item.id == itemId }?.item?.state?.favorite ?: false
        viewModelScope.launch {
            examPracticeRepository.updateFavorite(itemId, !currentFavorite)
                .onSuccess { remoteState ->
                    updateItems { item ->
                        if (item.item.id != itemId) {
                            item
                        } else {
                            item.copy(item = item.item.copy(state = remoteState))
                        }
                    }
                }
        }
    }

    fun finishSessionIfNeeded() {
        if (finished) return
        finished = true
        val state = uiState.value
        val report = buildPracticeReport(state.items)
        val summary = buildSessionSummary(state.items, report)
        if (summary.questionCount <= 0 || summary.completedCount <= 0) {
            return
        }
        if (state.isReadOnlyCache) {
            return
        }
        val durationMs = (SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtLeast(0L)
        viewModelScope.launch {
            practiceFacade.saveSessionRecord(
                PracticeSessionRecord(
                    id = 0L,
                    date = "",
                    mode = PracticeMode.EXAM,
                    entryType = PracticeEntryType.SELF,
                    entryCount = 1,
                    durationMs = durationMs,
                    createdAt = System.currentTimeMillis(),
                    wordIds = listOf(state.wordId),
                    questionCount = summary.questionCount,
                    completedCount = summary.completedCount,
                    correctCount = summary.correctCount,
                    submitCount = summary.submitCount
                )
            )
            practiceReportRepository.save(
                PracticeSessionReportRecord(
                    sessionId = sessionId,
                    kind = PracticeKind.EXAM,
                    report = report,
                    completedAtMillis = System.currentTimeMillis()
                )
            )
            examPracticeRepository.submitSession(
                ExamPracticeSessionSubmission(
                    wordId = state.wordId,
                    durationMs = durationMs,
                    questionCount = summary.questionCount,
                    completedCount = summary.completedCount,
                    correctCount = summary.correctCount,
                    submitCount = summary.submitCount,
                    items = buildSubmissionItems(state.items)
                )
            )
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
            totalCount = practice.totalCount,
            favoriteCount = practice.favoriteCount,
            wrongCount = practice.wrongCount,
            objectiveCount = practice.objectiveCount,
            isReadOnlyCache = practice.isReadOnlyCache,
            errorMessage = null
        )
        return base.copy(visibleItems = applyFilters(base))
    }

    private fun updateItems(transform: (WordExamPracticeItemUi) -> WordExamPracticeItemUi) {
        _uiState.update { state ->
            val nextItems = state.items.map(transform)
            val nextState = state.copy(
                items = nextItems,
                totalCount = nextItems.size,
                favoriteCount = nextItems.count { it.item.state?.favorite == true },
                wrongCount = nextItems.count { it.item.state?.wrongBook == true },
                objectiveCount = nextItems.count { item ->
                    item.item.questionType.isObjectiveType()
                }
            )
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
        updateItems { item ->
            if (item.item.id !in visibleItemIds) {
                item
            } else if (showAnswer) {
                item.copy(showAnswer = true, viewedAnswer = true)
            } else {
                item.copy(showAnswer = false)
            }
        }
    }

    private fun applyFilters(state: WordExamPracticeUiState): List<WordExamPracticeItemUi> {
        val showingPassageOnly = state.selectedTypes == setOf(ExamQuestionType.PASSAGE)
        val sorted = state.items.sortedWith(compareBy({ it.item.sortOrder }, { it.item.id }))
        if (showingPassageOnly) {
            return sorted.filter { item ->
                item.item.questionType == ExamQuestionType.PASSAGE &&
                    matchesCategory(item, state.selectedCategory)
            }
        }

        val answerableItems = sorted.filter { item ->
            item.item.questionType != ExamQuestionType.PASSAGE &&
                matchesType(item, state.selectedTypes) &&
                matchesCategory(item, state.selectedCategory) &&
                matchesStatus(item, state.statusFilter)
        }
        val groupKeys = answerableItems.mapNotNull { it.item.groupKey }.toSet()

        return sorted.filter { item ->
            if (item.item.questionType == ExamQuestionType.PASSAGE) {
                !item.item.groupKey.isNullOrBlank() && item.item.groupKey in groupKeys
            } else {
                answerableItems.any { it.item.id == item.item.id }
            }
        }
    }

    private fun matchesType(
        item: WordExamPracticeItemUi,
        selectedTypes: Set<ExamQuestionType>
    ): Boolean {
        return selectedTypes.isEmpty() || item.item.questionType in selectedTypes
    }

    private fun matchesCategory(
        item: WordExamPracticeItemUi,
        selectedCategory: ExamCategory?
    ): Boolean {
        return selectedCategory == null || item.item.examCategory == selectedCategory
    }

    private fun matchesStatus(item: WordExamPracticeItemUi, filter: ExamStatusFilter): Boolean {
        val state = item.item.state
        return when (filter) {
            ExamStatusFilter.ALL -> true
            ExamStatusFilter.FAVORITE -> state?.favorite == true
            ExamStatusFilter.WRONG -> state?.wrongBook == true
        }
    }

    private fun buildSubmissionItems(items: List<WordExamPracticeItemUi>): List<ExamPracticeAnswerSubmission> {
        return items.mapNotNull { item ->
            when (item.item.questionType) {
                ExamQuestionType.SINGLE_CHOICE -> item.selectedOptionIndex?.let { selectedIndex ->
                    ExamPracticeAnswerSubmission(
                        itemId = item.item.id,
                        answerIndexes = listOf(selectedIndex),
                        viewedAnswer = item.viewedAnswer,
                        submitCount = item.submitCount.coerceAtLeast(1)
                    )
                }

                ExamQuestionType.CLOZE -> if (item.selectedClozeAnswers.isEmpty()) {
                    null
                } else {
                    ExamPracticeAnswerSubmission(
                        itemId = item.item.id,
                        answers = item.selectedClozeAnswers,
                        viewedAnswer = item.viewedAnswer,
                        submitCount = item.submitCount.coerceAtLeast(1)
                    )
                }

                ExamQuestionType.MATCHING -> if (item.matchingPairs.isEmpty()) {
                    null
                } else {
                    ExamPracticeAnswerSubmission(
                        itemId = item.item.id,
                        answerIndexes = item.matchingPairs.entries
                            .sortedBy { it.key }
                            .map { it.value },
                        viewedAnswer = item.viewedAnswer,
                        submitCount = item.submitCount.coerceAtLeast(1)
                    )
                }

                ExamQuestionType.TRANSLATION -> if (item.translationInput.isBlank()) {
                    null
                } else {
                    ExamPracticeAnswerSubmission(
                        itemId = item.item.id,
                        answers = listOf(item.translationInput.trim()),
                        viewedAnswer = item.viewedAnswer,
                        submitCount = item.submitCount.coerceAtLeast(1)
                    )
                }

                ExamQuestionType.PASSAGE -> null
            }
        }
    }

    private fun buildSessionSummary(
        items: List<WordExamPracticeItemUi>,
        report: PracticeReport = buildPracticeReport(items)
    ): ExamPracticeSessionSummary {
        return sessionPolicy.buildSummary(
            items = items.map { it.item },
            submissionsByItemId = buildSubmissionsByItemId(items),
            report = report
        )
    }

    private fun buildPracticeReport(items: List<WordExamPracticeItemUi>): PracticeReport {
        return sessionPolicy.buildReport(
            items = items.map { it.item },
            submissionsByItemId = buildSubmissionsByItemId(items)
        )
    }

    private fun buildSubmissionsByItemId(
        items: List<WordExamPracticeItemUi>
    ): Map<Long, ExamPracticeAnswerSubmission> {
        return buildSubmissionItems(items).associateBy { it.itemId }
    }

    private fun ExamQuestionType.isObjectiveType(): Boolean {
        return this == ExamQuestionType.SINGLE_CHOICE ||
            this == ExamQuestionType.CLOZE ||
            this == ExamQuestionType.MATCHING
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> {
    return if (contains(value)) this - value else this + value
}
