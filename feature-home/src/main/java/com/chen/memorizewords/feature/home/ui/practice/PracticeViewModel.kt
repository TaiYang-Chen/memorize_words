package com.chen.memorizewords.feature.home.ui.practice

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.floating.service.FloatingReviewFacade
import com.chen.memorizewords.domain.practice.service.PracticeFacade
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.PracticeAvailability
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.core.navigation.FloatingWordActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val practiceFacade: PracticeFacade,
    private val resourceProvider: ResourceProvider,
    private val practiceUiMapper: PracticeUiMapper,
    private val floatingReviewFacade: FloatingReviewFacade
) : BaseViewModel() {

    sealed interface Route {
        data class ToPracticeMode(val mode: PracticeMode) : Route
        data class DispatchFloatingAction(val action: String) : Route
    }

    private val recentStats =
        practiceFacade.getRecentPracticeDurationStats(7)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val recentSessions =
        practiceFacade.getRecentSessionRecords(7)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val todayDurationMs =
        practiceFacade.getTodayPracticeDurationMs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val totalDurationMs =
        practiceFacade.getPracticeTotalDurationMs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val continuousDays =
        practiceFacade.getContinuousPracticeDays()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val floatingEnabled: StateFlow<Boolean> =
        floatingReviewFacade.observeSettings()
            .map { it.enabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val todayDurationText: StateFlow<String> =
        todayDurationMs
            .map(practiceUiMapper::formatDuration)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                resourceProvider.getString(R.string.home_duration_minutes, 0)
            )

    val todayDurationHmsText: StateFlow<String> =
        todayDurationMs
            .map(practiceUiMapper::formatDurationHms)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                "00:00:00"
            )

    val totalDurationText: StateFlow<String> =
        totalDurationMs
            .map(practiceUiMapper::formatDuration)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                resourceProvider.getString(R.string.home_duration_minutes, 0)
            )

    val totalDurationMinutesText: StateFlow<String> =
        totalDurationMs
            .map(practiceUiMapper::formatDurationMinutes)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                "0"
            )

    val continuousDaysText: StateFlow<String> =
        continuousDays
            .map { it.toString() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0")

    val increasePercentText: StateFlow<String> =
        recentStats
            .map(practiceUiMapper::formatIncreasePercent)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0%")

    val recentRecords: StateFlow<List<PracticeSessionRecordUi>> =
        recentSessions
            .map(practiceUiMapper::buildSessionUi)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dashboardUi: StateFlow<PracticeDashboardUi> =
        combine(
            todayDurationMs,
            continuousDays,
            recentStats,
            totalDurationMs
        ) { todayDuration, streakDays, stats, totalDuration ->
            practiceUiMapper.buildDashboardUi(
                todayDurationMs = todayDuration,
                continuousDays = streakDays,
                recentStats = stats,
                totalDurationMs = totalDuration
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            practiceUiMapper.buildDashboardUi(
                todayDurationMs = 0L,
                continuousDays = 0,
                recentStats = emptyList(),
                totalDurationMs = 0L
            )
        )

    fun openListening() {
        tryOpenPractice(PracticeMode.LISTENING)
    }

    fun openShadowing() {
        tryOpenPractice(PracticeMode.SHADOWING)
    }

    fun openSpelling() {
        tryOpenPractice(PracticeMode.SPELLING)
    }

    fun openAudioLoop() {
        tryOpenPractice(PracticeMode.AUDIO_LOOP)
    }

    fun onFloatingEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            val current = floatingReviewFacade.getSettings()
            if (current.enabled == enabled) return@launch
            floatingReviewFacade.saveSettings(current.copy(enabled = enabled))
            navigateRoute(
                Route.DispatchFloatingAction(
                    action = if (enabled) {
                        FloatingWordActions.ACTION_START
                    } else {
                        FloatingWordActions.ACTION_STOP
                    }
                )
            )
        }
    }

    private fun tryOpenPractice(mode: PracticeMode) {
        viewModelScope.launch {
            when (practiceFacade.getPracticeAvailability()) {
                PracticeAvailability.AVAILABLE -> navigateRoute(Route.ToPracticeMode(mode))
                PracticeAvailability.NO_BOOK -> {
                    showToast(resourceProvider.getString(R.string.home_practice_no_book))
                }
                PracticeAvailability.NO_WORDS -> {
                    showToast(resourceProvider.getString(R.string.home_practice_no_words))
                }
            }
        }
    }
}

data class PracticeSessionRecordUi(
    val id: Long,
    val titleText: String,
    val subtitleText: String,
    val iconRes: Int,
    val iconTintRes: Int
)
