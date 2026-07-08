package com.chen.memorizewords.feature.home.ui.practice

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.model.membership.MembershipFeatureAccess
import com.chen.memorizewords.domain.account.usecase.membership.ObserveMembershipStatusUseCase
import com.chen.memorizewords.domain.account.usecase.membership.ResolveMembershipFeatureAccessUseCase
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
    private val floatingReviewFacade: FloatingReviewFacade,
    observeMembershipStatusUseCase: ObserveMembershipStatusUseCase,
    private val resolveMembershipFeatureAccessUseCase: ResolveMembershipFeatureAccessUseCase
) : BaseViewModel() {

    sealed interface Route {
        data class ToPracticeMode(val mode: PracticeMode) : Route
        data class DispatchFloatingAction(val action: String) : Route
        data object ToFloatingSettings : Route
        data object ToMembership : Route
        data object RequestFloatingOverlayPermission : Route
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

    private val membershipStatus =
        observeMembershipStatusUseCase()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                null
            )

    val floatingEnabled: StateFlow<Boolean> =
        combine(
            floatingReviewFacade.observeSettings().map { it.enabled },
            membershipStatus
        ) { enabled, status ->
            enabled && status?.active == true
        }
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

    init {
        viewModelScope.launch {
            membershipStatus.collect { status ->
                if (status != null && !status.active) {
                    disableFloatingIfNeeded()
                }
            }
        }
    }

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
            if (enabled && !canUseFloatingReview()) {
                navigateRoute(Route.ToMembership)
                return@launch
            }
            setFloatingEnabled(enabled)
        }
    }

    fun onFloatingSwitchChecked(canDrawOverlays: Boolean) {
        viewModelScope.launch {
            if (!canUseFloatingReview()) {
                navigateRoute(Route.ToMembership)
                return@launch
            }
            if (canDrawOverlays) {
                setFloatingEnabled(true)
            } else {
                navigateRoute(Route.RequestFloatingOverlayPermission)
            }
        }
    }

    fun openFloatingSettings() {
        viewModelScope.launch {
            if (canUseFloatingReview()) {
                navigateRoute(Route.ToFloatingSettings)
            } else {
                navigateRoute(Route.ToMembership)
            }
        }
    }

    private suspend fun canUseFloatingReview(): Boolean {
        return resolveMembershipFeatureAccessUseCase(MembershipFeature.FLOATING_REVIEW) ==
            MembershipFeatureAccess.ALLOWED
    }

    private suspend fun setFloatingEnabled(enabled: Boolean) {
        val current = floatingReviewFacade.getSettings()
        if (current.enabled == enabled && current.autoStartOnAppLaunch == enabled) return
        floatingReviewFacade.saveSettings(
            current.copy(
                enabled = enabled,
                autoStartOnAppLaunch = enabled
            )
        )
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

    private suspend fun disableFloatingIfNeeded() {
        val current = floatingReviewFacade.getSettings()
        if (!current.enabled) return
        floatingReviewFacade.saveSettings(
            current.copy(
                enabled = false,
                autoStartOnAppLaunch = false
            )
        )
        navigateRoute(Route.DispatchFloatingAction(FloatingWordActions.ACTION_STOP))
    }

    private fun tryOpenPractice(mode: PracticeMode) {
        viewModelScope.launch {
            when (practiceFacade.getPracticeAvailability()) {
                PracticeAvailability.AVAILABLE -> navigateRoute(Route.ToPracticeMode(mode))
                PracticeAvailability.NO_BOOK -> {
                    showToast(resourceProvider.getString(R.string.home_practice_no_book))
                }
                PracticeAvailability.CONTENT_NOT_READY -> {
                    showToast("词书内容准备中")
                }
                PracticeAvailability.CONTENT_FAILED -> {
                    showToast("词书下载失败，请重试")
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
