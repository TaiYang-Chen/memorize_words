package com.chen.memorizewords.feature.home.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.study.record.DailyStudyDetail
import com.chen.memorizewords.domain.model.study.record.CheckInType
import com.chen.memorizewords.domain.model.study.record.MakeUpCheckInException
import com.chen.memorizewords.domain.usecase.word.study.GetDayStudyDetailUseCase
import com.chen.memorizewords.domain.usecase.word.study.MakeUpCheckInUseCase
import com.chen.memorizewords.feature.home.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    val uiState: StateFlow<DayStudyDetailUi> =
        combine(
            getDayStudyDetailUseCase(date),
            makeUpSubmitting
        ) { detail, submitting ->
                DayStudyDetailUi(
                    dateText = resourceProvider.getString(R.string.home_day_detail_date, detail.date),
                    newCountText = resourceProvider.getString(
                        R.string.home_day_detail_new_count,
                        detail.newCount
                    ),
                    reviewCountText = resourceProvider.getString(
                        R.string.home_day_detail_review_count,
                        detail.reviewCount
                    ),
                    durationText = resourceProvider.getString(
                        R.string.home_day_detail_duration,
                        formatDuration(detail.durationMs)
                    ),
                    planStatusText = resolvePlanStatusText(
                        isNewDone = detail.isNewPlanCompleted,
                        isReviewDone = detail.isReviewPlanCompleted
                    ),
                    checkInStatusText = resolveCheckInStatusText(detail),
                    showCheckInStatus = detail.checkInRecord != null || detail.canMakeUp,
                    showCheckInButton = detail.canMakeUp,
                    makeUpButtonText = resolveMakeUpButtonText(detail.availableMakeupCardCount),
                    makeUpButtonEnabled = detail.canMakeUp && !submitting,
                    isEmptyDay = !detail.hasStudy,
                    newWords = detail.newWords.map {
                        DayStudyWordItemUi(
                            word = it.word,
                            definition = it.definition
                        )
                    },
                    reviewWords = detail.reviewWords.map {
                        DayStudyWordItemUi(
                            word = it.word,
                            definition = it.definition
                        )
                    }
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                DayStudyDetailUi(
                    dateText = "",
                    newCountText = resourceProvider.getString(R.string.home_day_detail_new_count, 0),
                    reviewCountText = resourceProvider.getString(R.string.home_day_detail_review_count, 0),
                    durationText = resourceProvider.getString(
                        R.string.home_day_detail_duration,
                        resourceProvider.getString(R.string.home_duration_minutes, 0)
                    ),
                    planStatusText = resourceProvider.getString(R.string.home_day_detail_plan_none_done),
                    checkInStatusText = "",
                    showCheckInStatus = false,
                    showCheckInButton = false,
                    makeUpButtonText = resourceProvider.getString(R.string.home_day_detail_makeup_button_pending),
                    makeUpButtonEnabled = false,
                    isEmptyDay = true,
                    newWords = emptyList(),
                    reviewWords = emptyList()
                )
            )

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
                R.string.home_day_detail_makeup_button_with_count,
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

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
        if (totalMinutes < 60L) {
            return resourceProvider.getString(R.string.home_duration_minutes, totalMinutes)
        }
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (minutes > 0L) {
            resourceProvider.getString(R.string.home_duration_hours_minutes, hours, minutes)
        } else {
            resourceProvider.getString(R.string.home_duration_hours, hours)
        }
    }

    private fun resolvePlanStatusText(isNewDone: Boolean, isReviewDone: Boolean): String {
        return when {
            isNewDone && isReviewDone -> resourceProvider.getString(R.string.home_day_detail_plan_all_done)
            isNewDone -> resourceProvider.getString(R.string.home_day_detail_plan_new_done)
            isReviewDone -> resourceProvider.getString(R.string.home_day_detail_plan_review_done)
            else -> resourceProvider.getString(R.string.home_day_detail_plan_none_done)
        }
    }

    companion object {
        const val ARG_DATE = "arg_date"
    }
}
