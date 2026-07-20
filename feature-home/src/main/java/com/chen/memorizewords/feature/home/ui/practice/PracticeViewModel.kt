package com.chen.memorizewords.feature.home.ui.practice

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.model.membership.MembershipFeatureAccess
import com.chen.memorizewords.domain.account.usecase.membership.ObserveMembershipStatusUseCase
import com.chen.memorizewords.domain.account.usecase.membership.ResolveMembershipFeatureAccessUseCase
import com.chen.memorizewords.domain.floating.service.FloatingReviewFacade
import com.chen.memorizewords.domain.floating.service.FloatingActivationCoordinator
import com.chen.memorizewords.domain.floating.service.FloatingActivationIneligibleException
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.FloatingActivationContinuation
import com.chen.memorizewords.domain.floating.model.FloatingActivationPhase
import com.chen.memorizewords.domain.floating.model.FloatingActivationPreparation
import com.chen.memorizewords.domain.floating.model.FloatingActivationSnapshot
import com.chen.memorizewords.domain.floating.model.FloatingActivationSource
import com.chen.memorizewords.domain.practice.service.PracticeFacade
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.PracticeAvailability
import com.chen.memorizewords.domain.practice.usage.ObservePracticeUsageUseCase
import com.chen.memorizewords.domain.practice.usage.PracticeUsageState
import com.chen.memorizewords.domain.practice.usage.RefreshPracticeUsageUseCase
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.core.navigation.FloatingWordActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    private val floatingActivationCoordinator: FloatingActivationCoordinator,
    observeMembershipStatusUseCase: ObserveMembershipStatusUseCase,
    private val resolveMembershipFeatureAccessUseCase: ResolveMembershipFeatureAccessUseCase,
    observePracticeUsageUseCase: ObservePracticeUsageUseCase,
    private val refreshPracticeUsageUseCase: RefreshPracticeUsageUseCase,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    sealed interface Route {
        data class ToPracticeMode(val mode: PracticeMode) : Route
        data class DispatchFloatingAction(
            val action: String,
            val activationRequestId: String? = null
        ) : Route
        data object ToFloatingSettings : Route
        data object ToMembership : Route
        data object RequestFloatingOverlayPermission : Route
        data class ToCharacterSelection(val activationRequestId: String?) : Route
        data class ContinueDownloadedActivation(val activationRequestId: String) : Route
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

    val practiceUsageState: StateFlow<PracticeUsageState> = observePracticeUsageUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeUsageState.Unknown)

    private val floatingActivationSnapshot = floatingActivationCoordinator.observeSnapshot()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FloatingActivationSnapshot()
        )

    val floatingSetupUi: StateFlow<FloatingPetSetupUi> = floatingActivationSnapshot
        .map(::mapFloatingSetupUi)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FloatingPetSetupUi()
        )

    private val continuationRequestsInFlight = mutableSetOf<String>()

    val floatingEnabled: StateFlow<Boolean> =
        combine(
            floatingReviewFacade.observeSettings().map { it.enabled },
            membershipStatus,
            floatingActivationCoordinator.observeCurrentPackInstalled()
        ) { enabled, status, packInstalled ->
            enabled && status?.active == true && packInstalled
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
        refreshPracticeUsage()
        viewModelScope.launch {
            membershipStatus.collect { status ->
                if (status != null && !status.active) {
                    disableFloatingIfNeeded()
                }
            }
        }
        viewModelScope.launch {
            var hasInitialSnapshot = false
            val promptedRequests = mutableSetOf<String>()
            floatingActivationCoordinator.observeSnapshot().collect { snapshot ->
                if (!hasInitialSnapshot) {
                    hasInitialSnapshot = true
                    return@collect
                }
                val pending = snapshot.pending ?: return@collect
                if (
                    pending.committedAtMs == null &&
                    pending.source == FloatingActivationSource.HOME &&
                    snapshot.phase == FloatingActivationPhase.READY &&
                    snapshot.download?.status == CharacterPackDownloadStatus.COMPLETED &&
                    promptedRequests.add(pending.requestId)
                ) {
                    navigateRoute(Route.ContinueDownloadedActivation(pending.requestId))
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
        if (evaluation == null) return 10
        if (evaluation.remaining <= 0) return 10
        val tierDefault = if (evaluation.tier.name == "MEMBER") 20 else 10
        return minOf(tierDefault, evaluation.remaining).coerceAtLeast(1)
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
        if (enabled) return
        viewModelScope.launch {
            try {
                floatingActivationCoordinator.disableFloating()
                navigateRoute(Route.DispatchFloatingAction(FloatingWordActions.ACTION_STOP))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showToast(resourceProvider.getString(R.string.feature_home_floating_start_failed))
            }
        }
    }

    fun onFloatingSwitchChecked(canDrawOverlays: Boolean) {
        viewModelScope.launch {
            prepareFloatingActivation(canDrawOverlays)
        }
    }

    fun onFloatingPermissionResult(granted: Boolean) {
        val requestId = savedStateHandle.get<String>(KEY_PERMISSION_REQUEST_ID)
        if (granted && requestId != null) {
            continueFloatingActivation(canDrawOverlays = true, requestId = requestId)
        } else if (!granted) {
            denyFloatingPermission(requestId)
        }
    }

    fun onFloatingPermissionDialogCancelled() {
        denyFloatingPermission(savedStateHandle[KEY_PERMISSION_REQUEST_ID])
    }

    fun onFloatingHostResumed(
        canDrawOverlays: Boolean,
        allowPermissionPrompt: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                floatingActivationCoordinator.reconcileEnabledState()
                val snapshot = floatingActivationSnapshot.value
                val pending = snapshot.pending
                if (
                    snapshot.phase == FloatingActivationPhase.READY &&
                    pending?.source == FloatingActivationSource.HOME &&
                    (pending.committedAtMs != null || canDrawOverlays || allowPermissionPrompt)
                ) {
                    continueFloatingActivation(
                        canDrawOverlays,
                        pending.requestId
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showToast(resourceProvider.getString(R.string.feature_home_floating_start_failed))
            }
        }
    }

    fun downloadResolvedCharacter(expectedRequestId: String? = null) {
        viewModelScope.launch {
            val pending = floatingActivationSnapshot.value.pending ?: return@launch
            if (
                pending.source != FloatingActivationSource.HOME ||
                (expectedRequestId != null && pending.requestId != expectedRequestId)
            ) return@launch
            floatingActivationCoordinator.startResolvedCharacterDownload().onFailure { error ->
                if (error is FloatingActivationIneligibleException) {
                    navigateRoute(Route.ToMembership)
                } else {
                    showToast(resourceProvider.getString(R.string.feature_home_floating_download_failed))
                }
            }
        }
    }

    fun cancelFloatingSetup(expectedRequestId: String? = null) {
        viewModelScope.launch {
            try {
                val snapshot = floatingActivationSnapshot.value
                val pending = snapshot.pending ?: return@launch
                if (
                    pending.source != FloatingActivationSource.HOME ||
                    (expectedRequestId != null && pending.requestId != expectedRequestId)
                ) return@launch
                val requestId = pending.requestId
                val cancelled = if (snapshot.phase.isBusy()) {
                    floatingActivationCoordinator.cancelPendingDownload(requestId)
                } else {
                    floatingActivationCoordinator.cancelPending(requestId)
                }
                if (cancelled) {
                    savedStateHandle[KEY_PERMISSION_REQUEST_ID] = null
                    navigateRoute(Route.DispatchFloatingAction(FloatingWordActions.ACTION_STOP))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showToast(resourceProvider.getString(R.string.feature_home_floating_start_failed))
            }
        }
    }

    fun openCharacterSelection(expectedRequestId: String? = null) {
        viewModelScope.launch {
            try {
                val pending = floatingActivationSnapshot.value.pending
                if (
                    pending != null &&
                    (
                        pending.source != FloatingActivationSource.HOME ||
                            (expectedRequestId != null && pending.requestId != expectedRequestId)
                        )
                ) return@launch
                val requestId = pending?.requestId
                    ?: floatingActivationCoordinator.getPendingRequestId(FloatingActivationSource.HOME)
                floatingActivationCoordinator.recordOtherCharacterSelected(requestId)
                navigateRoute(
                    Route.ToCharacterSelection(requestId)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showToast(resourceProvider.getString(R.string.feature_home_floating_start_failed))
            }
        }
    }

    fun recordFloatingSetupShown(requestId: String?) {
        if (
            requestId == null ||
            savedStateHandle.get<String>(KEY_SETUP_SHOWN_REQUEST_ID) == requestId
        ) return
        savedStateHandle[KEY_SETUP_SHOWN_REQUEST_ID] = requestId
        viewModelScope.launch {
            try {
                floatingActivationCoordinator.recordSetupShown(requestId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Setup rendering remains available even when optional telemetry storage fails.
            }
        }
    }

    fun continueDownloadedActivation(
        requestId: String,
        canDrawOverlays: Boolean
    ) {
        continueFloatingActivation(
            canDrawOverlays = canDrawOverlays,
            requestId = requestId
        )
    }

    fun retryFloatingCatalog(
        canDrawOverlays: Boolean,
        expectedRequestId: String? = null
    ) {
        viewModelScope.launch {
            val pending = floatingActivationSnapshot.value.pending
            if (
                pending != null &&
                (
                    pending.source != FloatingActivationSource.HOME ||
                        (expectedRequestId != null && pending.requestId != expectedRequestId)
                    )
            ) return@launch
            prepareFloatingActivation(canDrawOverlays)
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

    private suspend fun prepareFloatingActivation(canDrawOverlays: Boolean) {
        try {
            when (floatingActivationCoordinator.prepareActivation(FloatingActivationSource.HOME)) {
                FloatingActivationPreparation.READY_FOR_PERMISSION -> {
                    floatingActivationCoordinator
                        .getPendingRequestId(FloatingActivationSource.HOME)
                        ?.let { continueFloatingActivation(canDrawOverlays, it) }
                }
                FloatingActivationPreparation.NEEDS_DOWNLOAD,
                FloatingActivationPreparation.NO_CHARACTER_AVAILABLE -> Unit
                FloatingActivationPreparation.SELECTION_REQUIRED -> {
                    showToast(resourceProvider.getString(R.string.feature_home_floating_selection_required))
                    navigateRoute(Route.ToCharacterSelection(null))
                }
                FloatingActivationPreparation.INELIGIBLE -> navigateRoute(Route.ToMembership)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            showToast(resourceProvider.getString(R.string.feature_home_floating_start_failed))
        }
    }

    private fun continueFloatingActivation(
        canDrawOverlays: Boolean,
        requestId: String
    ) {
        if (!continuationRequestsInFlight.add(requestId)) return
        viewModelScope.launch {
            try {
                when (
                    floatingActivationCoordinator.continueActivation(
                        canDrawOverlays = canDrawOverlays,
                        expectedRequestId = requestId
                    )
                ) {
                    FloatingActivationContinuation.ACTIVATED -> {
                        savedStateHandle[KEY_PERMISSION_REQUEST_ID] = null
                        navigateRoute(
                            Route.DispatchFloatingAction(
                                action = FloatingWordActions.ACTION_START,
                                activationRequestId = requestId
                            )
                        )
                    }
                    FloatingActivationContinuation.REQUIRES_PERMISSION -> {
                        savedStateHandle[KEY_PERMISSION_REQUEST_ID] = requestId
                        navigateRoute(Route.RequestFloatingOverlayPermission)
                    }
                    FloatingActivationContinuation.WAITING_FOR_CHARACTER -> Unit
                    FloatingActivationContinuation.NO_PENDING_REQUEST,
                    FloatingActivationContinuation.STALE_REQUEST -> {
                        if (savedStateHandle.get<String>(KEY_PERMISSION_REQUEST_ID) == requestId) {
                            savedStateHandle[KEY_PERMISSION_REQUEST_ID] = null
                        }
                    }
                    FloatingActivationContinuation.INELIGIBLE -> {
                        savedStateHandle[KEY_PERMISSION_REQUEST_ID] = null
                        navigateRoute(Route.ToMembership)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (savedStateHandle.get<String>(KEY_PERMISSION_REQUEST_ID) == requestId) {
                    savedStateHandle[KEY_PERMISSION_REQUEST_ID] = null
                }
                showToast(resourceProvider.getString(R.string.feature_home_floating_start_failed))
            } finally {
                continuationRequestsInFlight.remove(requestId)
            }
        }
    }

    fun onForegroundServiceStartRejected(requestId: String?) {
        if (requestId == null) return
        viewModelScope.launch {
            try {
                val rejected = floatingActivationCoordinator.rejectForegroundServiceStart(requestId)
                if (savedStateHandle.get<String>(KEY_PERMISSION_REQUEST_ID) == requestId) {
                    savedStateHandle[KEY_PERMISSION_REQUEST_ID] = null
                }
                if (rejected) {
                    navigateRoute(Route.DispatchFloatingAction(FloatingWordActions.ACTION_STOP))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The fragment presents the immediate failure message. Avoid a duplicate toast.
            }
        }
    }

    private fun denyFloatingPermission(requestId: String?) {
        viewModelScope.launch {
            try {
                val denied = requestId != null &&
                    floatingActivationCoordinator.denyOverlayPermission(requestId)
                if (denied) {
                    navigateRoute(Route.DispatchFloatingAction(FloatingWordActions.ACTION_STOP))
                }
                if (savedStateHandle.get<String>(KEY_PERMISSION_REQUEST_ID) == requestId) {
                    savedStateHandle[KEY_PERMISSION_REQUEST_ID] = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showToast(resourceProvider.getString(R.string.feature_home_floating_start_failed))
            }
        }
    }

    private suspend fun disableFloatingIfNeeded() {
        try {
            val current = floatingReviewFacade.getSettings()
            floatingActivationCoordinator.reconcileEnabledState()
            if (current.enabled) {
                navigateRoute(Route.DispatchFloatingAction(FloatingWordActions.ACTION_STOP))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            showToast(resourceProvider.getString(R.string.feature_home_floating_start_failed))
        }
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

    private companion object {
        const val KEY_PERMISSION_REQUEST_ID = "practice_floating_permission_request_id"
        const val KEY_SETUP_SHOWN_REQUEST_ID = "practice_floating_setup_shown_request_id"
    }
}

data class FloatingPetSetupUi(
    val requestId: String? = null,
    val source: FloatingActivationSource? = null,
    val packId: String? = null,
    val packName: String = "",
    val description: String = "",
    val previewUrl: String? = null,
    val isDefault: Boolean = false,
    val sizeBytes: Long = 0L,
    val phase: FloatingActivationPhase = FloatingActivationPhase.IDLE,
    val committed: Boolean = false,
    val progress: Int = 0,
    val errorMessage: String? = null
) {
    val hasCharacter: Boolean get() = packName.isNotBlank()
    val isBusy: Boolean get() = phase == FloatingActivationPhase.QUEUED ||
        phase == FloatingActivationPhase.DOWNLOADING ||
        phase == FloatingActivationPhase.INSTALLING
    internal val shouldShowSetupDialog: Boolean
        get() = requestId != null &&
            source == FloatingActivationSource.HOME &&
            !committed &&
            phase.requiresFloatingSetupDialog()
}

private fun mapFloatingSetupUi(snapshot: FloatingActivationSnapshot): FloatingPetSetupUi {
    return FloatingPetSetupUi(
        requestId = snapshot.pending?.requestId,
        source = snapshot.pending?.source,
        packId = snapshot.target?.packId ?: snapshot.pending?.targetPackId,
        packName = snapshot.target?.displayName.orEmpty(),
        description = snapshot.target?.description.orEmpty(),
        previewUrl = snapshot.target?.previewUrl,
        isDefault = snapshot.target?.isDefault == true,
        sizeBytes = snapshot.target?.packageSizeBytes ?: 0L,
        phase = snapshot.phase,
        committed = snapshot.pending?.committedAtMs != null,
        progress = snapshot.download?.progress ?: 0,
        errorMessage = snapshot.download?.errorMessage
    )
}

private fun FloatingActivationPhase.isBusy(): Boolean {
    return this == FloatingActivationPhase.QUEUED ||
        this == FloatingActivationPhase.DOWNLOADING ||
        this == FloatingActivationPhase.INSTALLING
}

private fun FloatingActivationPhase.requiresFloatingSetupDialog(): Boolean {
    return this == FloatingActivationPhase.NEEDS_DOWNLOAD ||
        this == FloatingActivationPhase.QUEUED ||
        this == FloatingActivationPhase.DOWNLOADING ||
        this == FloatingActivationPhase.INSTALLING ||
        this == FloatingActivationPhase.FAILED
}

data class PracticeSessionRecordUi(
    val id: Long,
    val titleText: String,
    val subtitleText: String,
    val iconRes: Int,
    val iconTintRes: Int
)
