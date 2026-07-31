package com.chen.memorizewords.feature.home.ui.practice

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.model.membership.MembershipFeatureAccess
import com.chen.memorizewords.domain.account.usecase.membership.ObserveMembershipStatusUseCase
import com.chen.memorizewords.domain.account.usecase.membership.ResolveMembershipFeatureAccessUseCase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeError
import com.chen.memorizewords.domain.floating.model.FloatingRuntimePhase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSnapshot
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSource
import com.chen.memorizewords.domain.floating.service.FloatingRuntimeController
import com.chen.memorizewords.domain.practice.PracticeAvailability
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.service.PracticeFacade
import com.chen.memorizewords.domain.practice.usage.ObservePracticeUsageUseCase
import com.chen.memorizewords.domain.practice.usage.PracticeUsageState
import com.chen.memorizewords.domain.practice.usage.RefreshPracticeUsageUseCase
import com.chen.memorizewords.feature.home.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val practiceFacade: PracticeFacade,
    private val resourceProvider: ResourceProvider,
    private val practiceUiMapper: PracticeUiMapper,
    private val floatingRuntimeController: FloatingRuntimeController,
    observeMembershipStatusUseCase: ObserveMembershipStatusUseCase,
    private val resolveMembershipFeatureAccessUseCase: ResolveMembershipFeatureAccessUseCase,
    observePracticeUsageUseCase: ObservePracticeUsageUseCase,
    private val refreshPracticeUsageUseCase: RefreshPracticeUsageUseCase
) : BaseViewModel() {

    sealed interface Route {
        data class ToPracticeMode(val mode: PracticeMode) : Route
        data object ToFloatingSettings : Route
        data object ToMembership : Route
        data object RequestFloatingOverlayPermission : Route
        data object ToCharacterSelection : Route
    }

    private val recentStats =
        practiceFacade.getRecentPracticeDurationStats(PRACTICE_COMPARISON_DAYS)
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
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val practiceUsageState: StateFlow<PracticeUsageState> = observePracticeUsageUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeUsageState.Unknown)

    private val floatingRuntime = floatingRuntimeController.observeRuntime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FloatingRuntimeSnapshot())

    val floatingRuntimeUi: StateFlow<FloatingRuntimeUi> = floatingRuntime
        .map(::mapFloatingRuntimeUi)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FloatingRuntimeUi())

    val todayDurationText: StateFlow<String> =
        todayDurationMs.map(practiceUiMapper::formatDuration).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            resourceProvider.getString(R.string.home_duration_minutes, 0)
        )

    val todayDurationHmsText: StateFlow<String> =
        todayDurationMs.map(practiceUiMapper::formatDurationHms).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            "00:00:00"
        )

    val totalDurationText: StateFlow<String> =
        totalDurationMs.map(practiceUiMapper::formatDuration).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            resourceProvider.getString(R.string.home_duration_minutes, 0)
        )

    val totalDurationMinutesText: StateFlow<String> =
        totalDurationMs.map(practiceUiMapper::formatDurationMinutes).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            "0"
        )

    val continuousDaysText: StateFlow<String> =
        continuousDays.map(Int::toString).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            "0"
        )

    val increasePercentText: StateFlow<String> =
        recentStats.map(practiceUiMapper::formatIncreasePercent).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            "0%"
        )

    val recentRecords: StateFlow<List<PracticeSessionRecordUi>> =
        recentSessions.map(practiceUiMapper::buildSessionUi).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val dashboardUi: StateFlow<PracticeDashboardUi> = combine(
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
        refreshPracticeUsage()
        viewModelScope.launch {
            membershipStatus.collect { status ->
                if (status != null && !status.active) floatingRuntimeController.requestStop()
            }
        }
        viewModelScope.launch {
            var promptedPermissionSessionId: String? = null
            var promptedCharacterSessionId: String? = null
            var presentedFailureSessionId: String? = null
            floatingRuntime.collect { snapshot ->
                val session = snapshot.session ?: return@collect
                when (session.phase) {
                    FloatingRuntimePhase.AWAITING_PERMISSION -> {
                        if (promptedPermissionSessionId != session.sessionId) {
                            promptedPermissionSessionId = session.sessionId
                            navigateRoute(Route.RequestFloatingOverlayPermission)
                        }
                    }
                    FloatingRuntimePhase.AWAITING_CHARACTER -> {
                        if (promptedCharacterSessionId != session.sessionId) {
                            promptedCharacterSessionId = session.sessionId
                            navigateRoute(Route.ToCharacterSelection)
                        }
                    }
                    FloatingRuntimePhase.FAILED -> {
                        if (
                            session.error == FloatingRuntimeError.MEMBERSHIP_REQUIRED &&
                            presentedFailureSessionId != session.sessionId
                        ) {
                            presentedFailureSessionId = session.sessionId
                            navigateRoute(Route.ToMembership)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun refreshPracticeUsage() {
        viewModelScope.launch { refreshPracticeUsageUseCase() }
    }

    fun recommendedShadowingCount(): Int {
        val evaluation = when (val state = practiceUsageState.value) {
            is PracticeUsageState.Available -> state.usage.evaluation
            is PracticeUsageState.Stale -> state.usage.evaluation
            is PracticeUsageState.Exhausted -> state.usage.evaluation
            else -> null
        }
        if (evaluation == null || evaluation.remaining <= 0) return 10
        val tierDefault = if (evaluation.tier.name == "MEMBER") 20 else 10
        return minOf(tierDefault, evaluation.remaining).coerceAtLeast(1)
    }

    fun openListening() = tryOpenPractice(PracticeMode.LISTENING)

    fun openShadowing() = tryOpenPractice(PracticeMode.SHADOWING)

    fun openSpelling() = tryOpenPractice(PracticeMode.SPELLING)

    fun openAudioLoop() = tryOpenPractice(PracticeMode.AUDIO_LOOP)

    fun onUnavailableFeatureClicked() {
        showToast(resourceProvider.getString(R.string.feature_home_practice_v2_unavailable))
    }

    fun onFloatingEnabledChanged(enabled: Boolean) {
        if (enabled) return
        viewModelScope.launch { floatingRuntimeController.requestStop() }
    }

    fun onFloatingSwitchChecked() {
        viewModelScope.launch { floatingRuntimeController.requestStart(FloatingRuntimeSource.HOME) }
    }

    fun onFloatingPermissionResult(granted: Boolean) {
        viewModelScope.launch { floatingRuntimeController.submitPermissionResult(granted) }
    }

    fun onFloatingPermissionDialogCancelled() = onFloatingPermissionResult(granted = false)

    fun onFloatingHostResumed() {
        viewModelScope.launch { floatingRuntimeController.reconcileForeground() }
    }

    fun openFloatingSettings() {
        viewModelScope.launch {
            if (canUseFloatingReview()) navigateRoute(Route.ToFloatingSettings)
            else navigateRoute(Route.ToMembership)
        }
    }

    private fun mapFloatingRuntimeUi(snapshot: FloatingRuntimeSnapshot): FloatingRuntimeUi {
        val session = snapshot.session
        val phase = snapshot.phase
        val subtitle = when (phase) {
            FloatingRuntimePhase.IDLE -> resourceProvider.getString(R.string.feature_home_floating_subtitle)
            FloatingRuntimePhase.RESOLVING -> resourceProvider.getString(R.string.feature_home_floating_preparing)
            FloatingRuntimePhase.AWAITING_PERMISSION ->
                resourceProvider.getString(R.string.feature_home_floating_permission_required)
            FloatingRuntimePhase.AWAITING_CHARACTER ->
                resourceProvider.getString(R.string.feature_home_floating_character_required)
            FloatingRuntimePhase.DOWNLOADING -> resourceProvider.getString(
                R.string.feature_home_floating_downloading,
                session?.progress ?: 0
            )
            FloatingRuntimePhase.INSTALLING ->
                resourceProvider.getString(R.string.feature_home_floating_installing)
            FloatingRuntimePhase.STARTING -> resourceProvider.getString(R.string.feature_home_floating_starting)
            FloatingRuntimePhase.RUNNING -> resourceProvider.getString(R.string.feature_home_floating_running)
            FloatingRuntimePhase.STOPPING -> resourceProvider.getString(R.string.feature_home_floating_stopping)
            FloatingRuntimePhase.FAILED -> resourceProvider.getString(R.string.feature_home_floating_failed)
        }
        return FloatingRuntimeUi(
            phase = phase,
            checked = phase !in setOf(FloatingRuntimePhase.IDLE, FloatingRuntimePhase.FAILED, FloatingRuntimePhase.STOPPING),
            switchEnabled = phase in setOf(
                FloatingRuntimePhase.IDLE,
                FloatingRuntimePhase.RUNNING,
                FloatingRuntimePhase.FAILED
            ),
            subtitle = subtitle,
            progress = session?.progress ?: 0
        )
    }

    private suspend fun canUseFloatingReview(): Boolean =
        resolveMembershipFeatureAccessUseCase(MembershipFeature.FLOATING_REVIEW) ==
            MembershipFeatureAccess.ALLOWED

    private fun tryOpenPractice(mode: PracticeMode) {
        viewModelScope.launch {
            when (practiceFacade.getPracticeAvailability()) {
                PracticeAvailability.AVAILABLE -> navigateRoute(Route.ToPracticeMode(mode))
                PracticeAvailability.NO_BOOK -> showToast(resourceProvider.getString(R.string.home_practice_no_book))
                PracticeAvailability.CONTENT_NOT_READY -> showToast("Content is still being prepared")
                PracticeAvailability.CONTENT_FAILED -> showToast("Content download failed. Please try again")
                PracticeAvailability.NO_WORDS -> showToast(resourceProvider.getString(R.string.home_practice_no_words))
            }
        }
    }
}

data class FloatingRuntimeUi(
    val phase: FloatingRuntimePhase = FloatingRuntimePhase.IDLE,
    val checked: Boolean = false,
    val switchEnabled: Boolean = true,
    val subtitle: String = "",
    val progress: Int = 0
)

data class PracticeSessionRecordUi(
    val id: Long,
    val titleText: String,
    val subtitleText: String,
    val iconRes: Int,
    val iconTintRes: Int
)
