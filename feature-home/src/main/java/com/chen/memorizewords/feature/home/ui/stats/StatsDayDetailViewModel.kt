package com.chen.memorizewords.feature.home.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.study.model.record.DailyStudyDetail
import com.chen.memorizewords.domain.study.model.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.study.model.record.CheckInType
import com.chen.memorizewords.domain.study.model.record.MakeUpCheckInException
import com.chen.memorizewords.domain.study.usecase.word.study.GetDayStudyDetailUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.MakeUpCheckInUseCase
import com.chen.memorizewords.feature.home.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StatsDayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getDayStudyDetailUseCase: GetDayStudyDetailUseCase,
    private val makeUpCheckInUseCase: MakeUpCheckInUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private val date: String = savedStateHandle.get<String>(ARG_DATE).orEmpty()
    private val makeUpSubmitting = MutableStateFlow(false)
    private val newWordsExpanded = MutableStateFlow(false)
    private val reviewWordsExpanded = MutableStateFlow(false)

    val uiState: StateFlow<DayStudyDetailUi> =
        combine(
            getDayStudyDetailUseCase(date),
            makeUpSubmitting,
            newWordsExpanded,
            reviewWordsExpanded
        ) { detail, submitting, isNewExpanded, isReviewExpanded ->
                val newWords = detail.newWords.map { it.toItemUi() }
                val reviewWords = detail.reviewWords.map { it.toItemUi() }
                val visibleNewWords = if (isNewExpanded) newWords else newWords.take(PREVIEW_WORD_LIMIT)
                val visibleReviewWords = if (isReviewExpanded) {
                    reviewWords
                } else {
                    reviewWords.take(PREVIEW_WORD_LIMIT)
                }
                val duration = formatDurationParts(detail.durationMs)
                DayStudyDetailUi(
                    dateText = formatDateText(detail.date),
                    newCountValue = detail.newCount.toString(),
                    reviewCountValue = detail.reviewCount.toString(),
                    durationValue = duration.value,
                    durationUnit = duration.unit,
                    planStatusText = resolvePlanBannerText(
                        isNewDone = detail.isNewPlanCompleted,
                        isReviewDone = detail.isReviewPlanCompleted
                    ),
                    planStatusSubtitle = resolvePlanBannerSubtitle(detail),
                    checkInStatusText = resolveCheckInStatusText(detail),
                    showCheckInStatus = detail.checkInRecord != null || detail.canMakeUp,
                    showCheckInButton = detail.canMakeUp,
                    makeUpButtonText = resolveMakeUpButtonText(detail.availableMakeupCardCount),
                    makeUpButtonEnabled = detail.canMakeUp &&
                        detail.availableMakeupCardCount != null &&
                        !submitting,
                    isEmptyDay = !detail.hasStudy,
                    newWordsTitle = resourceProvider.getString(
                        R.string.home_day_detail_new_words_with_count,
                        detail.newCount
                    ),
                    reviewWordsTitle = resourceProvider.getString(
                        R.string.home_day_detail_review_words_with_count,
                        detail.reviewCount
                    ),
                    newWords = visibleNewWords,
                    reviewWords = visibleReviewWords,
                    showNewWordsMore = newWords.size > PREVIEW_WORD_LIMIT && !isNewExpanded,
                    showReviewWordsMore = reviewWords.size > PREVIEW_WORD_LIMIT && !isReviewExpanded,
                    newWordsMoreText = resourceProvider.getString(
                        R.string.home_day_detail_view_all_new_words,
                        newWords.size
                    ),
                    reviewWordsMoreText = resourceProvider.getString(
                        R.string.home_day_detail_view_all_review_words,
                        reviewWords.size
                    )
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                DayStudyDetailUi(
                    dateText = "",
                    newCountValue = "0",
                    reviewCountValue = "0",
                    durationValue = "0",
                    durationUnit = "m",
                    planStatusText = resourceProvider.getString(R.string.home_day_detail_plan_not_completed_short),
                    planStatusSubtitle = resourceProvider.getString(R.string.home_day_detail_keep_learning),
                    checkInStatusText = "",
                    showCheckInStatus = false,
                    showCheckInButton = false,
                    makeUpButtonText = resourceProvider.getString(R.string.home_day_detail_makeup_button_pending),
                    makeUpButtonEnabled = false,
                    isEmptyDay = true,
                    newWordsTitle = resourceProvider.getString(
                        R.string.home_day_detail_new_words_with_count,
                        0
                    ),
                    reviewWordsTitle = resourceProvider.getString(
                        R.string.home_day_detail_review_words_with_count,
                        0
                    ),
                    newWords = emptyList(),
                    reviewWords = emptyList(),
                    showNewWordsMore = false,
                    showReviewWordsMore = false,
                    newWordsMoreText = "",
                    reviewWordsMoreText = ""
                )
            )

    fun onNewWordsMoreClicked() {
        newWordsExpanded.update { true }
    }

    fun onReviewWordsMoreClicked() {
        reviewWordsExpanded.update { true }
    }

    fun onMakeUpClicked() {
        if (makeUpSubmitting.value) return
        viewModelScope.launch {
            makeUpSubmitting.value = true
            makeUpCheckInUseCase(date)
                .onSuccess {
                    showToast(resourceProvider.getString(R.string.home_day_detail_makeup_success))
                }
                .onFailure { failure ->
                    showToast(resolveMakeUpFailureMessage(failure))
                }
            makeUpSubmitting.value = false
        }
    }

    private fun resolveMakeUpFailureMessage(failure: Throwable): String {
        return when (failure) {
            MakeUpCheckInException.FutureDate -> {
                resourceProvider.getString(R.string.home_day_detail_makeup_future_date)
            }
            MakeUpCheckInException.BalanceUnknown -> {
                resourceProvider.getString(R.string.home_day_detail_makeup_balance_unknown)
            }
            MakeUpCheckInException.NoAvailableCard -> {
                resourceProvider.getString(R.string.home_day_detail_makeup_no_card)
            }
            else -> resourceProvider.getString(R.string.home_day_detail_makeup_failed)
        }
    }

    private fun resolveMakeUpButtonText(availableMakeupCardCount: Int?): String {
        return if (availableMakeupCardCount == null) {
            resourceProvider.getString(R.string.home_day_detail_makeup_button_pending)
        } else {
            resourceProvider.getString(
                R.string.home_day_detail_makeup_card_button_with_count,
                availableMakeupCardCount
            )
        }
    }

    private fun resolveCheckInStatusText(detail: DailyStudyDetail): String {
        val record = detail.checkInRecord
        return when {
            record?.type == CheckInType.AUTO -> {
                resourceProvider.getString(R.string.home_day_detail_checkin_auto)
            }
            record?.type == CheckInType.MAKEUP -> {
                resourceProvider.getString(R.string.home_day_detail_checkin_makeup)
            }
            detail.canMakeUp -> {
                resourceProvider.getString(R.string.home_day_detail_checkin_available)
            }
            else -> ""
        }
    }

    private fun resolvePlanBannerSubtitle(detail: DailyStudyDetail): String {
        val record = detail.checkInRecord
        return when {
            record?.type == CheckInType.AUTO -> {
                resourceProvider.getString(R.string.home_day_detail_auto_checkin_success)
            }
            record?.type == CheckInType.MAKEUP -> {
                resourceProvider.getString(R.string.home_day_detail_makeup_success)
            }
            detail.canMakeUp -> {
                resourceProvider.getString(R.string.home_day_detail_checkin_available)
            }
            detail.hasStudy -> {
                resourceProvider.getString(R.string.home_day_detail_keep_learning)
            }
            else -> resourceProvider.getString(R.string.home_day_detail_empty_short)
        }
    }

    private fun formatDurationParts(durationMs: Long): DurationParts {
        val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
        if (totalMinutes < 60L) {
            return DurationParts(totalMinutes.toString(), "m")
        }
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (minutes > 0L) {
            DurationParts("$hours:$minutes", "h")
        } else {
            DurationParts(hours.toString(), "h")
        }
    }

    private fun resolvePlanBannerText(isNewDone: Boolean, isReviewDone: Boolean): String {
        return when {
            isNewDone && isReviewDone -> resourceProvider.getString(R.string.home_day_detail_plan_all_done_short)
            isNewDone -> resourceProvider.getString(R.string.home_day_detail_plan_new_done_short)
            isReviewDone -> resourceProvider.getString(R.string.home_day_detail_plan_review_done_short)
            else -> resourceProvider.getString(R.string.home_day_detail_plan_not_completed_short)
        }
    }

    private fun formatDateText(rawDate: String): String {
        val parts = rawDate.split("-")
        if (parts.size != 3) return rawDate
        val year = parts[0].toIntOrNull() ?: return rawDate
        val month = parts[1].toIntOrNull() ?: return rawDate
        val day = parts[2].toIntOrNull() ?: return rawDate
        return resourceProvider.getString(R.string.home_day_detail_date_ymd, year, month, day)
    }

    private fun DailyStudyWordRecord.toItemUi(): DayStudyWordItemUi {
        return DayStudyWordItemUi(
            word = word,
            definition = definition,
            badgeText = word.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        )
    }

    companion object {
        const val ARG_DATE = "arg_date"
        private const val PREVIEW_WORD_LIMIT = 3
    }
}

private data class DurationParts(
    val value: String,
    val unit: String
)
