package com.chen.memorizewords.feature.wordbook.plan

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.domain.model.study.StudyPlan
import com.chen.memorizewords.domain.model.wordbook.WordBookInfo
import com.chen.memorizewords.domain.repository.WordOrderType
import com.chen.memorizewords.domain.usecase.wordbook.GetCurrentWordBookInfoFlowUseCase
import com.chen.memorizewords.domain.usecase.wordbook.GetStudyPlanFlowUseCase
import com.chen.memorizewords.domain.usecase.wordbook.ResetBookWordsUseCase
import com.chen.memorizewords.domain.usecase.wordbook.SaveStudyPlanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StudyPlanSettingViewModel @Inject constructor(
    getCurrentWordBookInfoFlowUseCase: GetCurrentWordBookInfoFlowUseCase,
    getStudyPlanFlowUseCase: GetStudyPlanFlowUseCase,
    private val saveStudyPlanUseCase: SaveStudyPlanUseCase,
    private val resetBookWordsUseCase: ResetBookWordsUseCase
) : BaseViewModel() {

    companion object {
        const val ACTION_RESET_BOOK_WORDS = "reset_book_words"
    }

    sealed interface Route {
        data class ToWordList(val bookId: Long) : Route
        data object ToMyWordBooks : Route
        data class ToModifyPlan(
            val newCount: Int,
            val reviewCount: Int
        ) : Route
    }

    val wordBookCardState: StateFlow<WordBookInfo> =
        getCurrentWordBookInfoFlowUseCase()
            .map { wordBook -> wordBook ?: WordBookInfo() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = WordBookInfo()
            )

    val planCountCardState: StateFlow<StudyPlan> =
        getStudyPlanFlowUseCase()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StudyPlan()
            )

    fun onPickWordList() {
        navigateRoute(Route.ToWordList(wordBookCardState.value.bookId))
    }

    fun onPickMyWordBook() {
        navigateRoute(Route.ToMyWordBooks)
    }

    fun onPickChangeStudyCount() {
        navigateRoute(
            Route.ToModifyPlan(
                newCount = planCountCardState.value.dailyNewCount,
                reviewCount = planCountCardState.value.dailyReviewCount
            )
        )
    }

    fun onSelectTestMode(mode: LearningTestMode) {
        updatePlan(
            transform = { current ->
                if (current.testMode == mode) current else current.copy(testMode = mode)
            }
        )
    }

    fun onSelectWordOrderType(type: WordOrderType) {
        updatePlan(
            transform = { current ->
                if (current.wordOrderType == type) current else current.copy(wordOrderType = type)
            },
            afterSave = {
            }
        )
    }

    private fun updatePlan(
        transform: (StudyPlan) -> StudyPlan,
        afterSave: (suspend () -> Unit)? = null
    ) {
        viewModelScope.launch {
            val current = planCountCardState.value
            val next = transform(current)
            if (next == current) return@launch
            saveStudyPlanUseCase(next)
            afterSave?.invoke()
        }

        showToast("学习计划已更新")
    }

    fun resetBookWord() {
        showConfirmDialog(
            action = ACTION_RESET_BOOK_WORDS,
            title = "确认重置进度吗?",
            message = "重置后，当前单词学习进度将被完全清空，所有已掌握单词将恢复为未学状态。此操作无法撤销。"
        )
    }

    fun onResetBookWordConfirmed() {
        viewModelScope.launch {
            resetBookWordsUseCase(wordBookCardState.value.bookId)
        }
    }
}
