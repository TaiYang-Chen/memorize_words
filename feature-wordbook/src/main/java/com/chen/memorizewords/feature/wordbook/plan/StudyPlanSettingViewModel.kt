package com.chen.memorizewords.feature.wordbook.plan

import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import com.chen.memorizewords.domain.wordbook.usecase.GetCurrentWordBookInfoFlowUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetStudyPlanFlowUseCase
import com.chen.memorizewords.domain.study.usecase.wordbook.ResetBookWordsUseCase
import com.chen.memorizewords.domain.wordbook.usecase.SaveStudyPlanUseCase
import com.chen.memorizewords.feature.wordbook.R
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

        fun availableStudyModes(): List<StudyModeUiModel> =
            LearningTestMode.entries.map(::studyModeUiModelFor)

        fun studyModeUiModelFor(mode: LearningTestMode): StudyModeUiModel {
            return when (mode) {
                LearningTestMode.MEANING_CHOICE -> StudyModeUiModel(
                    mode = mode,
                    titleRes = R.string.module_wordbook_study_mode_meaning_title,
                    descriptionRes = R.string.module_wordbook_study_mode_meaning_desc,
                    iconRes = R.drawable.module_wordbook_ic_study_mode_meaning
                )

                LearningTestMode.SPELLING -> StudyModeUiModel(
                    mode = mode,
                    titleRes = R.string.module_wordbook_study_mode_spelling_title,
                    descriptionRes = R.string.module_wordbook_study_mode_spelling_desc,
                    iconRes = R.drawable.module_wordbook_ic_study_mode_spelling
                )

                LearningTestMode.LISTENING -> StudyModeUiModel(
                    mode = mode,
                    titleRes = R.string.module_wordbook_study_mode_listening_title,
                    descriptionRes = R.string.module_wordbook_study_mode_listening_desc,
                    iconRes = R.drawable.module_wordbook_ic_study_mode_listening
                )
            }
        }

        @StringRes
        fun wordOrderLabelRes(type: WordOrderType): Int {
            return when (type) {
                WordOrderType.RANDOM -> R.string.module_wordbook_word_order_random
                WordOrderType.ALPHABETIC_ASC -> R.string.module_wordbook_word_order_alpha_asc
                WordOrderType.ALPHABETIC_DESC -> R.string.module_wordbook_word_order_alpha_desc
                WordOrderType.LENGTH_ASC -> R.string.module_wordbook_word_order_length_asc
                WordOrderType.LENGTH_DESC -> R.string.module_wordbook_word_order_length_desc
            }
        }
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
            .map { plan -> plan ?: StudyPlan() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StudyPlan()
            )

    val currentStudyModeCardState: StateFlow<StudyModeUiModel> =
        planCountCardState
            .map { plan -> studyModeUiModelFor(plan.testMode) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = studyModeUiModelFor(StudyPlan().testMode)
            )

    val currentWordOrderLabelRes: StateFlow<Int> =
        planCountCardState
            .map { plan -> wordOrderLabelRes(plan.wordOrderType) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = wordOrderLabelRes(StudyPlan().wordOrderType)
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
        updatePlan { current ->
            if (current.testMode == mode) current else current.copy(testMode = mode)
        }
    }

    fun onSelectWordOrderType(type: WordOrderType) {
        updatePlan { current ->
            if (current.wordOrderType == type) current else current.copy(wordOrderType = type)
        }
    }

    private fun updatePlan(transform: (StudyPlan) -> StudyPlan) {
        viewModelScope.launch {
            val current = planCountCardState.value
            val next = transform(current)
            if (next == current) return@launch
            saveStudyPlanUseCase(next)
            showToast("学习计划已更新")
        }
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
