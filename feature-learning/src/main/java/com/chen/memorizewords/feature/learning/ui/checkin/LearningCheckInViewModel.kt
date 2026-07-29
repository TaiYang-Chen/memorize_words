package com.chen.memorizewords.feature.learning.ui.checkin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.usecase.word.study.GetContinuousCheckInDaysUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetStudyTotalDurationUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetStudyTotalWordCountUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.ObserveCheckInRecordUseCase
import com.chen.memorizewords.feature.learning.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LearningCheckInViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCheckInRecord: ObserveCheckInRecordUseCase,
    getContinuousCheckInDaysUseCase: GetContinuousCheckInDaysUseCase,
    getStudyTotalDurationUseCase: GetStudyTotalDurationUseCase,
    getStudyTotalWordCountUseCase: GetStudyTotalWordCountUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    sealed interface Route {
        data class Share(val content: String) : Route
    }

    sealed interface Phase {
        data object Loading : Phase
        data class Success(val record: CheckInRecord) : Phase
        data object Exit : Phase
    }

    data class UiState(
        val phase: Phase = Phase.Loading,
        val title: String = "",
        val subtitle: String = "",
        val dateText: String = "",
        val streakValueText: String = "0",
        val totalDurationText: String = "",
        val totalWordsText: String = "0"
    ) {
        val isLoading: Boolean
            get() = phase == Phase.Loading

        val showContent: Boolean
            get() = phase is Phase.Success
    }

    private val businessDate = savedStateHandle.get<String>(BUSINESS_DATE_KEY).orEmpty()
    private val phase = observeCheckInRecord(businessDate)
        .map { record -> record?.let(Phase::Success) ?: Phase.Exit }
        .onEach { currentPhase ->
            if (currentPhase == Phase.Exit) finish()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Phase.Loading)

    val uiState: StateFlow<UiState> =
        combine(
            phase,
            getContinuousCheckInDaysUseCase(),
            getStudyTotalDurationUseCase(),
            getStudyTotalWordCountUseCase()
        ) { currentPhase, streakDays, totalDurationMs, totalWords ->
            when (currentPhase) {
                Phase.Loading -> UiState(
                    phase = Phase.Loading,
                    title = resourceProvider.getString(R.string.learning_check_in_loading_title),
                    subtitle = resourceProvider.getString(R.string.learning_check_in_loading_subtitle),
                    totalDurationText = formatDuration(totalDurationMs)
                )

                is Phase.Success -> UiState(
                    phase = currentPhase,
                    title = resourceProvider.getString(R.string.learning_check_in_success_title),
                    subtitle = resourceProvider.getString(R.string.learning_check_in_success_subtitle),
                    dateText = resourceProvider.getString(
                        R.string.learning_check_in_signed_date,
                        currentPhase.record.date
                    ),
                    streakValueText = streakDays.toString(),
                    totalDurationText = formatDuration(totalDurationMs),
                    totalWordsText = totalWords.toString()
                )

                Phase.Exit -> UiState(phase = Phase.Exit)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UiState(
                phase = Phase.Loading,
                title = resourceProvider.getString(R.string.learning_check_in_loading_title),
                subtitle = resourceProvider.getString(R.string.learning_check_in_loading_subtitle)
            )
        )

    fun onShareClicked() {
        val successState = uiState.value.phase as? Phase.Success ?: return
        navigateRoute(
            Route.Share(
                resourceProvider.getString(
                    R.string.learning_check_in_share_text,
                    successState.record.date,
                    uiState.value.streakValueText,
                    uiState.value.totalDurationText,
                    uiState.value.totalWordsText
                )
            )
        )
    }

    fun onBackHomeClicked() {
        finish()
    }

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) {
            resourceProvider.getString(R.string.learning_check_in_total_duration_hours_minutes, hours, minutes)
        } else {
            resourceProvider.getString(R.string.learning_check_in_total_duration_minutes, totalMinutes)
        }
    }

    private companion object {
        const val BUSINESS_DATE_KEY = "businessDate"
    }
}
