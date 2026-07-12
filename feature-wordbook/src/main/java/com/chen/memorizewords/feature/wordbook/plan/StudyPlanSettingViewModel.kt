package com.chen.memorizewords.feature.wordbook.plan

import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.wordbook.model.WordBookContentStatus
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import com.chen.memorizewords.domain.wordbook.usecase.GetCurrentWordBookInfoFlowUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetStudyPlanFlowUseCase
import com.chen.memorizewords.domain.wordbook.usecase.ObserveCurrentWordBookSelectionIdUseCase
import com.chen.memorizewords.domain.wordbook.usecase.ObserveWordBookContentStateUseCase
import com.chen.memorizewords.domain.wordbook.usecase.SaveStudyPlanUseCase
import com.chen.memorizewords.domain.wordbook.usecase.ResetCurrentWordBookProgressUseCase
import com.chen.memorizewords.feature.wordbook.R
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StudyPlanSettingViewModel @Inject constructor(
    getCurrentWordBookInfoFlowUseCase: GetCurrentWordBookInfoFlowUseCase,
    observeCurrentWordBookSelectionIdUseCase: ObserveCurrentWordBookSelectionIdUseCase,
    observeWordBookContentStateUseCase: ObserveWordBookContentStateUseCase,
    getStudyPlanFlowUseCase: GetStudyPlanFlowUseCase,
    private val saveStudyPlanUseCase: SaveStudyPlanUseCase,
    private val resetCurrentWordBookProgressUseCase: ResetCurrentWordBookProgressUseCase
) : BaseViewModel() {

    companion object {
        const val ACTION_RESET_BOOK_WORDS = "reset_book_words"

        fun availableStudyModes(): List<StudyModeUiModel> =
            listOf(studyModeUiModelFor(LearningTestMode.MEANING_CHOICE))

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

    private val currentSelectionId =
        observeCurrentWordBookSelectionIdUseCase()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    private val _isResetting = MutableStateFlow(false)
    val isResetting: StateFlow<Boolean> = _isResetting.asStateFlow()

    val wordBookCardState: StateFlow<StudyPlanWordBookCardState> =
        combine(
            currentSelectionId,
            getCurrentWordBookInfoFlowUseCase(),
            currentSelectionId.flatMapLatest { bookId ->
                if (bookId == null || bookId <= 0L) {
                    flowOf(null)
                } else {
                    observeWordBookContentStateUseCase(bookId)
                }
            }
        ) { selectedBookId, wordBook, contentState ->
            when {
                selectedBookId == null || selectedBookId <= 0L -> StudyPlanWordBookCardState.noSelection()
                wordBook == null -> StudyPlanWordBookCardState.syncing(selectedBookId)
                contentState?.status == WordBookContentStatus.READY -> {
                    StudyPlanWordBookCardState.fromWordBook(
                        bookId = selectedBookId,
                        title = wordBook.title,
                        category = wordBook.category,
                        imgUrl = wordBook.imgUrl,
                        totalWords = wordBook.totalWords,
                        learningWords = wordBook.learningWords,
                        masteredWords = wordBook.masteredWords,
                        correctCount = wordBook.correctCount,
                        wrongCount = wordBook.wrongCount,
                        studyDayCount = wordBook.studyDayCount,
                        statusText = "\u6b63\u5728\u5b66\u4e60",
                        canOpenWordList = true
                    )
                }
                contentState?.status == WordBookContentStatus.FAILED -> {
                    StudyPlanWordBookCardState.fromWordBook(
                        bookId = selectedBookId,
                        title = wordBook.title,
                        category = wordBook.category,
                        imgUrl = wordBook.imgUrl,
                        totalWords = wordBook.totalWords,
                        learningWords = wordBook.learningWords,
                        masteredWords = wordBook.masteredWords,
                        correctCount = wordBook.correctCount,
                        wrongCount = wordBook.wrongCount,
                        studyDayCount = wordBook.studyDayCount,
                        statusText = "\u4e0b\u8f7d\u5931\u8d25"
                    )
                }
                else -> {
                    StudyPlanWordBookCardState.fromWordBook(
                        bookId = selectedBookId,
                        title = wordBook.title,
                        category = wordBook.category,
                        imgUrl = wordBook.imgUrl,
                        totalWords = wordBook.totalWords,
                        learningWords = wordBook.learningWords,
                        masteredWords = wordBook.masteredWords,
                        correctCount = wordBook.correctCount,
                        wrongCount = wordBook.wrongCount,
                        studyDayCount = wordBook.studyDayCount,
                        statusText = "\u5185\u5bb9\u51c6\u5907\u4e2d"
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StudyPlanWordBookCardState.noSelection()
        )

    val planCountCardState: StateFlow<StudyPlan> =
        getStudyPlanFlowUseCase()
            .map { plan -> (plan ?: StudyPlan()).meaningChoiceOnly() }
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
        val state = wordBookCardState.value
        if (!state.canOpenWordList || state.bookId <= 0L) {
            showToast(state.statusText)
            return
        }
        navigateRoute(Route.ToWordList(state.bookId))
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
        if (mode != LearningTestMode.MEANING_CHOICE) return
        updatePlan { current ->
            if (current.testMode == LearningTestMode.MEANING_CHOICE) {
                current
            } else {
                current.copy(testMode = LearningTestMode.MEANING_CHOICE)
            }
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
            val next = transform(current).meaningChoiceOnly()
            if (next == current) return@launch
            saveStudyPlanUseCase(next)
            showToast("学习计划已更新")
        }
    }

    fun resetBookWord() {
        if (_isResetting.value) return
        val state = wordBookCardState.value
        if (state.bookId <= 0L || !state.canOpenWordList) {
            showToast("当前词书暂不可重置")
            return
        }
        if (!state.hasProgress) {
            showToast("当前词书暂无学习进度")
            return
        }
        showConfirmDialog(
            action = ACTION_RESET_BOOK_WORDS,
            title = "重置词书进度",
            message = "重置后数据不会恢复，是否确认重置？",
            confirmText = "确认重置",
            cancelText = "取消"
        )
    }

    fun onResetBookWordConfirmed() {
        if (_isResetting.value) return
        val target = wordBookCardState.value
        if (target.bookId <= 0L) return
        viewModelScope.launch {
            _isResetting.value = true
            showLoading("正在重置…")
            try {
                resetCurrentWordBookProgressUseCase(target.bookId)
                    .onSuccess { showToast("词书进度已重置") }
                    .onFailure { throwable ->
                        showToast(
                            if (throwable is java.io.IOException) {
                                "重置词书进度需要连接网络"
                            } else {
                                throwable.message?.takeIf(String::isNotBlank) ?: "重置失败，请稍后重试"
                            }
                        )
                    }
            } finally {
                hideLoading()
                _isResetting.value = false
            }
        }
    }
}

data class StudyPlanWordBookCardState(
    val bookId: Long,
    val title: String,
    val category: String,
    val imgUrl: String,
    val totalWords: Int,
    val learningWords: Int,
    val masteredWords: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val studyDayCount: Int,
    val statusText: String,
    val canOpenWordList: Boolean
) {
    val learnedWords: Int get() = learningWords + masteredWords
    val remainWords: Int get() = (totalWords - learningWords - masteredWords).coerceAtLeast(0)
    val hasProgress: Boolean
        get() = learningWords > 0 || masteredWords > 0 ||
            correctCount > 0 || wrongCount > 0 || studyDayCount > 0

    companion object {
        fun noSelection(): StudyPlanWordBookCardState =
            StudyPlanWordBookCardState(
                bookId = 0L,
                title = "\u5f53\u524d\u672a\u9009\u62e9\u8bcd\u4e66",
                category = "",
                imgUrl = "",
                totalWords = 0,
                learningWords = 0,
                masteredWords = 0,
                correctCount = 0,
                wrongCount = 0,
                studyDayCount = 0,
                statusText = "\u672a\u9009\u62e9",
                canOpenWordList = false
            )

        fun syncing(bookId: Long): StudyPlanWordBookCardState =
            StudyPlanWordBookCardState(
                bookId = bookId,
                title = "\u8bcd\u4e66\u4fe1\u606f\u540c\u6b65\u4e2d",
                category = "",
                imgUrl = "",
                totalWords = 0,
                learningWords = 0,
                masteredWords = 0,
                correctCount = 0,
                wrongCount = 0,
                studyDayCount = 0,
                statusText = "\u540c\u6b65\u4e2d",
                canOpenWordList = false
            )

        fun fromWordBook(
            bookId: Long,
            title: String,
            category: String,
            imgUrl: String,
            totalWords: Int,
            learningWords: Int,
            masteredWords: Int,
            correctCount: Int,
            wrongCount: Int,
            studyDayCount: Int,
            statusText: String,
            canOpenWordList: Boolean = false
        ): StudyPlanWordBookCardState =
            StudyPlanWordBookCardState(
                bookId = bookId,
                title = title,
                category = category,
                imgUrl = imgUrl,
                totalWords = totalWords,
                learningWords = learningWords,
                masteredWords = masteredWords,
                correctCount = correctCount,
                wrongCount = wrongCount,
                studyDayCount = studyDayCount,
                statusText = statusText,
                canOpenWordList = canOpenWordList
            )
    }
}

private fun StudyPlan.meaningChoiceOnly(): StudyPlan {
    return if (testMode == LearningTestMode.MEANING_CHOICE) {
        this
    } else {
        copy(testMode = LearningTestMode.MEANING_CHOICE)
    }
}
