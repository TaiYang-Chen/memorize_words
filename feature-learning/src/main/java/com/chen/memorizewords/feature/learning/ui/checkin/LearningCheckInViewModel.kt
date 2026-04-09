package com.chen.memorizewords.feature.learning.ui.checkin

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.study.record.CheckInRecord
import com.chen.memorizewords.domain.usecase.word.study.AutoCheckInTodayIfEligibleUseCase
import com.chen.memorizewords.domain.usecase.word.study.GetContinuousCheckInDaysUseCase
import com.chen.memorizewords.domain.usecase.word.study.GetStudyTotalDurationUseCase
import com.chen.memorizewords.domain.usecase.word.study.GetStudyTotalWordCountUseCase
import com.chen.memorizewords.feature.learning.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LearningCheckInViewModel @Inject constructor(
    private val autoCheckInTodayIfEligibleUseCase: AutoCheckInTodayIfEligibleUseCase,
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

    private val phase = MutableStateFlow<Phase>(Phase.Loading)
    private var initialized = false

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

    fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            autoCheckInTodayIfEligibleUseCase()
                .onSuccess { record ->
                    if (record == null) {
                        phase.value = Phase.Exit
                        finish()
                    } else {
                        phase.value = Phase.Success(record)
                    }
                }
                .onFailure {
                    phase.value = Phase.Exit
                    showToast(resourceProvider.getString(R.string.learning_check_in_failed))
                    finish()
                }
        }
    }

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
}
