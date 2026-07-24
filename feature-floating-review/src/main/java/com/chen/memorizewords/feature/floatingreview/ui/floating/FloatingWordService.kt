package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.chen.memorizewords.core.sprite.FloatingPetRenderHost
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.ui.ext.dpToPx
import coil.load
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.FloatingWordEntryExtras
import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.model.membership.MembershipFeatureAccess
import com.chen.memorizewords.domain.account.usecase.membership.ResolveMembershipFeatureAccessUseCase
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldConfig
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldType
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import com.chen.memorizewords.domain.floating.service.FloatingActivationCoordinator
import com.chen.memorizewords.domain.floating.service.FloatingActivationEvent
import com.chen.memorizewords.domain.floating.service.FloatingActivationEventReporter
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.floating.service.FloatingWordCardContent
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.feature.floatingreview.R
import com.chen.memorizewords.feature.floatingreview.ui.floating.pet.FloatingPetController
import com.chen.memorizewords.feature.floatingreview.ui.floating.pet.PetEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class FloatingCardActionState(
    val refreshEnabled: Boolean,
    val favoriteEnabled: Boolean,
    val copyEnabled: Boolean
)

internal data class FloatingWordAdvanceResult(
    val words: List<Word>,
    val nextIndex: Int,
    val word: Word?
)

internal fun resolveCardActionState(hasWord: Boolean): FloatingCardActionState {
    return FloatingCardActionState(
        refreshEnabled = hasWord,
        favoriteEnabled = hasWord,
        copyEnabled = hasWord
    )
}

internal fun resolveCardAlpha(cardOpacityPercent: Int): Float {
    return cardOpacityPercent.coerceIn(0, 100) / 100f
}

internal fun resolveBallAlpha(ballOpacityPercent: Int): Float {
    return ballOpacityPercent.coerceIn(0, 100) / 100f
}

internal fun resolveBallSizeScale(ballSizePercent: Int): Float {
    return ballSizePercent.coerceIn(1, 200) / 100f
}

internal fun isFloatingServiceOperationActive(
    stopping: Boolean,
    currentGeneration: Long,
    operationGeneration: Long
): Boolean {
    return !stopping && operationGeneration == currentGeneration
}

internal enum class FloatingServiceRunMode {
    NOT_STARTED,
    ENABLED,
    TEMPORARY_PREVIEW
}

internal data class FloatingServiceHealthSnapshot(
    val settingsEnabled: Boolean,
    val overlayPermissionGranted: Boolean,
    val membershipAllowed: Boolean,
    val characterPackUsable: Boolean
)

private data class CharacterPackInstallRevision(
    val packId: String,
    val packVersion: Int,
    val installedDirectory: String
)

internal fun shouldKeepFloatingServiceRunning(
    snapshot: FloatingServiceHealthSnapshot,
    runMode: FloatingServiceRunMode
): Boolean {
    val settingsAllowRun = snapshot.settingsEnabled ||
        runMode == FloatingServiceRunMode.TEMPORARY_PREVIEW
    return settingsAllowRun &&
        snapshot.overlayPermissionGranted &&
        snapshot.membershipAllowed &&
        snapshot.characterPackUsable
}

internal fun shouldReportFloatingStarted(
    alreadyReported: Boolean,
    reportInProgress: Boolean,
    runMode: FloatingServiceRunMode,
    ballViewAttached: Boolean,
    cardViewAttached: Boolean
): Boolean {
    return !alreadyReported &&
        !reportInProgress &&
        runMode == FloatingServiceRunMode.ENABLED &&
        ballViewAttached &&
        cardViewAttached
}

internal fun shouldReplaceFloatingStartedReport(
    reportInProgress: Boolean,
    activeRequestId: String?,
    incomingRequestId: String?
): Boolean {
    return reportInProgress &&
        incomingRequestId != null &&
        activeRequestId != incomingRequestId
}

internal fun shouldStopColdAppearanceRequest(
    ballViewAttached: Boolean,
    lifecycleOperationInProgress: Boolean
): Boolean = !ballViewAttached && !lifecycleOperationInProgress

internal fun isFloatingAppearanceOnlyAction(action: String?): Boolean {
    return action == FloatingWordActions.ACTION_APPLY_BALL_APPEARANCE ||
        action == FloatingWordActions.ACTION_APPLY_CHARACTER_PACK
}

internal fun shouldAcknowledgeManagementPackReload(
    requestedPackId: String?,
    downloadRequestId: String?,
    selectedPackId: String?,
    loadedPackId: SpritePackId?
): Boolean {
    return !requestedPackId.isNullOrBlank() &&
        !downloadRequestId.isNullOrBlank() &&
        requestedPackId == selectedPackId &&
        loadedPackId?.value == selectedPackId
}

internal fun shouldDisableActivationAfterSurfaceFailure(
    failure: RuntimeException,
    overlayPermissionGranted: Boolean
): Boolean = failure is SecurityException || !overlayPermissionGranted

internal fun advanceFloatingWordSequence(
    words: List<Word>,
    currentIndex: Int,
    orderType: FloatingWordOrderType,
    shuffleWords: (List<Word>) -> List<Word> = { it.shuffled() }
): FloatingWordAdvanceResult {
    if (words.isEmpty()) {
        return FloatingWordAdvanceResult(
            words = words,
            nextIndex = 0,
            word = null
        )
    }

    val resolvedIndex = if (currentIndex in words.indices) currentIndex else 0
    val currentWord = words[resolvedIndex]
    val reachedEnd = resolvedIndex + 1 >= words.size
    val nextWords = if (reachedEnd && orderType == FloatingWordOrderType.RANDOM) {
        shuffleWords(words)
    } else {
        words
    }

    return FloatingWordAdvanceResult(
        words = nextWords,
        nextIndex = if (reachedEnd) 0 else resolvedIndex + 1,
        word = currentWord
    )
}

internal fun resolveBallPositionForSettings(
    settings: FloatingWordSettings,
    bounds: FloatingMovementBounds,
    previousBounds: FloatingMovementBounds?,
    dockManager: FloatingDockManager = FloatingDockManager()
): FloatingBallPosition {
    settings.dockState?.let { dockState ->
        dockManager.resolveDocked(
            bounds = bounds,
            config = settings.dockConfig,
            dockState = dockState
        )?.let { docked ->
            return docked.position
        }
    }
    if (previousBounds != null) {
        dockManager.resolveAnchoredFreePosition(
            previousBounds = previousBounds,
            newBounds = bounds,
            x = settings.floatingBallX,
            y = settings.floatingBallY
        )?.let { anchoredPosition ->
            return anchoredPosition
        }
    }
    if (settings.floatingBallX != 0 || settings.floatingBallY != 0) {
        return dockManager.clampToFree(bounds, settings.floatingBallX, settings.floatingBallY)
    }
    return FloatingBallPosition(
        x = bounds.freeRight,
        y = ((bounds.freeTop + bounds.freeBottom) / 2f).roundToInt()
    )
}

@AndroidEntryPoint
class FloatingWordService : Service() {

    companion object {
        const val ACTION_START = FloatingWordActions.ACTION_START
        const val ACTION_STOP = FloatingWordActions.ACTION_STOP
        const val ACTION_REFRESH = FloatingWordActions.ACTION_REFRESH
        const val ACTION_PREVIEW_CARD = FloatingWordActions.ACTION_PREVIEW_CARD
        const val ACTION_APPLY_BALL_APPEARANCE = FloatingWordActions.ACTION_APPLY_BALL_APPEARANCE
        const val ACTION_APPLY_CHARACTER_PACK = FloatingWordActions.ACTION_APPLY_CHARACTER_PACK

        private const val CHANNEL_ID = "floating_word_review_channel"
        private const val NOTIFICATION_ID = 5321
        private const val EMPTY_PLACEHOLDER = "-"
        private const val PREVIEW_AUTO_HIDE_DELAY_MS = 1_200L
        private const val RUNTIME_HEALTH_CHECK_INTERVAL_MS = 60_000L
    }

    @Inject
    lateinit var floatingWordController: FloatingWordController

    @Inject
    lateinit var resolveMembershipFeatureAccessUseCase: ResolveMembershipFeatureAccessUseCase

    @Inject
    lateinit var floatingPetController: FloatingPetController

    @Inject
    lateinit var floatingActivationCoordinator: FloatingActivationCoordinator

    @Inject
    lateinit var characterPackRepository: CharacterPackRepository

    @Inject
    lateinit var floatingActivationEventReporter: FloatingActivationEventReporter

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dockManager = FloatingDockManager()
    private val speechLayoutEngine = FloatingSpeechLayoutEngine()
    private lateinit var windowManager: WindowManager

    private var ballView: View? = null
    private var cardView: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var characterPackRevisionJob: Job? = null
    private var cardParams: WindowManager.LayoutParams? = null
    private var settingsJob: Job? = null
    private var runtimeHealthJob: Job? = null
    private var previewAutoHideJob: Job? = null
    private var previewAutoHidePending = false
    private var cardLoadJob: Job? = null
    private var wordRefreshJob: Job? = null
    private var notificationUpdateJob: Job? = null
    private var notificationUpdateGeneration = 0L
    private var pendingNotificationContent: String? = null
    private var lastDeliveredNotificationContent: String? = null
    private var hiddenCardPrefetchJob: Job? = null
    private var hiddenCardPrefetchGeneration = 0L
    private var preparedNextCard: PreparedNextCard? = null


    private var cardRequestGeneration = 0L
    private var wordRefreshGeneration = 0L
    private var cardRequestedVisible = false
    private var wordSequenceRefreshPending = true
    private var loadedWordSequenceKey: WordSequenceKey? = null

    private var words: List<Word> = emptyList()
    private var currentIndex = 0
    private var currentWord: Word? = null
    private var currentDefinitions: List<WordDefinitions> = emptyList()
    private var currentSettings: FloatingWordSettings = FloatingWordSettings()
    private var settingsRevision = 0L
    private var operationGeneration = 0L
    private var stopping = false
    private var lifecycleOperationJob: Job? = null
    private var runMode = FloatingServiceRunMode.NOT_STARTED
    private var hasReportedFloatingStarted = false
    private var floatingStartedReportJob: Job? = null
    private var floatingStartedReportRequestId: String? = null
    private var loadedCharacterPackRevision: CharacterPackInstallRevision? = null
    private var floatingStartedReportAttempt = 0L
    private var floatingSurfaceGeneration = 0L
    private val managementPackReloadRequestsInFlight = mutableSetOf<String>()

    private var isDragging = false
    private var lastBallTapEventTimeMillis: Long? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var dragStartBallX = 0
    private var dragStartBallY = 0
    private var ballGestureDetector: GestureDetector? = null
    private var lastMovementBounds: FloatingMovementBounds? = null
    private var cachedCardWidth = 0
    private var cachedCardHeight = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        ensureChannel()
        settingsJob = serviceScope.launch {
            floatingWordController.observeSettings().collect { settings ->
                val observedGeneration = operationGeneration
                updateCurrentSettings(settings)
                if (
                    runMode == FloatingServiceRunMode.ENABLED &&
                    !settings.enabled
                ) {
                    val latest = try {
                        floatingWordController.getSettings()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        settings
                    }
                    updateCurrentSettings(latest)
                    if (!latest.enabled && isServiceOperationActive(observedGeneration)) {
                        stopFloating(requestGeneration = observedGeneration)
                    }
                    return@collect
                }
                if (
                    runMode != FloatingServiceRunMode.NOT_STARTED &&
                    settings.selectedCharacterPackId.isNullOrBlank()
                ) {
                    serviceScope.launch {
                        if (!isServiceOperationActive(observedGeneration)) return@launch
                        disableActivationBestEffort()
                        if (isServiceOperationActive(observedGeneration)) {
                            stopFloating(requestGeneration = observedGeneration)
                        }
                    }
                    return@collect
                }
                if (
                    runMode == FloatingServiceRunMode.TEMPORARY_PREVIEW &&
                    settings.enabled
                ) {
                    runMode = FloatingServiceRunMode.ENABLED
                }
                applyFloatingAppearance()
                if (ballView != null && !isDragging) {
                    applyBallSize()
                    reconcileBallPosition(persistIfNeeded = false)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val activationRequestId = intent
            ?.getStringExtra(FloatingWordEntryExtras.EXTRA_ACTIVATION_REQUEST_ID)
            ?.takeIf { action == ACTION_START && it.isNotBlank() }
        val requestedCharacterPackId = intent
            ?.getStringExtra(FloatingWordEntryExtras.EXTRA_CHARACTER_PACK_ID)
            ?.takeIf { action == ACTION_APPLY_CHARACTER_PACK && it.isNotBlank() }
        val downloadRequestId = intent
            ?.getStringExtra(FloatingWordEntryExtras.EXTRA_DOWNLOAD_REQUEST_ID)
            ?.takeIf { action == ACTION_APPLY_CHARACTER_PACK && it.isNotBlank() }
        if (action == ACTION_STOP) {
            stopFloating(startId = startId)
            return START_NOT_STICKY
        }
        val isAppearanceOnly = isFloatingAppearanceOnlyAction(action)
        if (stopping) {
            if (isAppearanceOnly) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            stopping = false
        }
        if (
            isAppearanceOnly &&
            shouldStopColdAppearanceRequest(
                ballViewAttached = ballView?.isAttachedToWindow == true,
                lifecycleOperationInProgress = lifecycleOperationJob?.isActive == true
            )
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val correlatedPackReloadRequestId = downloadRequestId?.takeIf {
            action == ACTION_APPLY_CHARACTER_PACK && requestedCharacterPackId != null
        }
        if (
            correlatedPackReloadRequestId != null &&
            !managementPackReloadRequestsInFlight.add(correlatedPackReloadRequestId)
        ) {
            return START_STICKY
        }
        if (!isAppearanceOnly) {
            lifecycleOperationJob?.cancel()
            runtimeHealthJob?.cancel()
            runtimeHealthJob = null
            operationGeneration++
        }
        val requestGeneration = operationGeneration
        if (!isAppearanceOnly) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(getString(R.string.module_floating_review_notification_ready))
                )
            } catch (failure: RuntimeException) {
                serviceScope.launch {
                    handleSurfaceFailure(
                        failure = failure,
                        requestGeneration = requestGeneration,
                        startId = startId
                    )
                }
                return START_NOT_STICKY
            }
        }
        val operationJob = serviceScope.launch {
            try {
                if (!isServiceOperationActive(requestGeneration)) return@launch
                val membershipAllowed = canUseFloatingReview()
                if (!isServiceOperationActive(requestGeneration)) return@launch
                if (!membershipAllowed) {
                    disableActivationBestEffort()
                    stopFloating(requestGeneration = requestGeneration)
                    return@launch
                }
                if (
                    !isAppearanceOnly &&
                    action != ACTION_PREVIEW_CARD
                ) {
                    val canStartCurrent = floatingActivationCoordinator.canStartCurrent()
                    if (!isServiceOperationActive(requestGeneration)) return@launch
                    if (!canStartCurrent) {
                        floatingActivationCoordinator.disableIfPackMissing()
                        stopFloating(requestGeneration = requestGeneration)
                        return@launch
                    }
                }
                if (!isServiceOperationActive(requestGeneration)) return@launch
                when (action) {
                    ACTION_APPLY_BALL_APPEARANCE ->
                        applyBallAppearanceIfAttached(requestGeneration, startId)
                    ACTION_APPLY_CHARACTER_PACK ->
                        if (ensureForegroundAndViews(requestGeneration)) {
                            applyCharacterPack(
                                requestGeneration = requestGeneration,
                                requestedPackId = requestedCharacterPackId,
                                downloadRequestId = downloadRequestId
                            )
                        }
                    ACTION_PREVIEW_CARD ->
                        if (
                            ensureForegroundAndViews(
                                operationGeneration = requestGeneration,
                                allowDisabledPreview = true
                            )
                        ) {
                            previewCard()
                        }
                    ACTION_REFRESH ->
                        if (ensureForegroundAndViews(requestGeneration)) refreshWords(showNext = false)
                    else ->
                        if (
                            ensureForegroundAndViews(
                                operationGeneration = requestGeneration,
                                activationRequestId = activationRequestId
                            )
                        ) {
                            refreshWords(showNext = false)
                        }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep a committed activation pending so the next foreground transition can retry.
                stopFloating(requestGeneration = requestGeneration)
            } finally {
                correlatedPackReloadRequestId?.let(
                    managementPackReloadRequestsInFlight::remove
                )
            }
        }
        if (!isAppearanceOnly) lifecycleOperationJob = operationJob
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reconcileBallPosition(persistIfNeeded = true)
    }

    override fun onDestroy() {
        stopping = true
        operationGeneration++
        removeViews()
        floatingPetController.release()
        settingsJob?.cancel()
        runtimeHealthJob?.cancel()
        previewAutoHideJob?.cancel()
        characterPackRevisionJob?.cancel()
        lifecycleOperationJob = null
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            floatingPetController.trimMemory()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun ensureForegroundAndViews(
        operationGeneration: Long,
        allowDisabledPreview: Boolean = false,
        activationRequestId: String? = null
    ): Boolean {
        if (!isServiceOperationActive(operationGeneration)) return false
        resolveLatestSettings()
        if (!isServiceOperationActive(operationGeneration)) return false
        if (!Settings.canDrawOverlays(this)) {
            disableActivationBestEffort()
            stopFloating(requestGeneration = operationGeneration)
            return false
        }
        val hasUsablePack = floatingActivationCoordinator.hasUsablePack()
        if (!isServiceOperationActive(operationGeneration)) return false
        if (!hasUsablePack) {
            floatingActivationCoordinator.disableIfPackMissing()
            stopFloating(requestGeneration = operationGeneration)
            return false
        }
        val settings = resolveLatestSettings()
        val selectedPackId = settings.selectedCharacterPackId?.takeIf { it.isNotBlank() }
        if (selectedPackId == null) {
            disableActivationBestEffort()
            stopFloating(requestGeneration = operationGeneration)
            return false
        }
        val targetRunMode = when {
            settings.enabled -> FloatingServiceRunMode.ENABLED
            allowDisabledPreview &&
                (runMode == FloatingServiceRunMode.TEMPORARY_PREVIEW || ballView == null) ->
                FloatingServiceRunMode.TEMPORARY_PREVIEW
            else -> {
                stopFloating(requestGeneration = operationGeneration)
                return false
            }
        }
        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(getString(R.string.module_floating_review_notification_ready))
            )
            if (!isServiceOperationActive(operationGeneration)) return false
            ensureViews(SpritePackId(selectedPackId))
            val installed = try {
                characterPackRepository.getInstalled(selectedPackId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            loadAndFinalizeCharacterPack(selectedPackId, installed)
            if (
                !isServiceOperationActive(operationGeneration) ||
                !floatingPetController.isPackReady(SpritePackId(selectedPackId))
            ) {
                throw IllegalStateException(
                    "Floating pet renderer did not reach a validated first frame"
                )
            }
        } catch (failure: RuntimeException) {
            handleSurfaceFailure(failure, requestGeneration = operationGeneration)
            return false
        }
        runMode = targetRunMode
        reportFloatingStartedIfNeeded(activationRequestId)
        startCharacterPackRevisionMonitoring(operationGeneration)
        startRuntimeHealthMonitoring(operationGeneration)
        applyFloatingAppearance()
        reconcileBallPosition(persistIfNeeded = true)
        return true
    }

    private fun stopFloating(requestGeneration: Long? = null, startId: Int? = null) {
        if (
            requestGeneration != null &&
            !isServiceOperationActive(requestGeneration)
        ) return
        stopping = true
        runMode = FloatingServiceRunMode.NOT_STARTED
        operationGeneration++
        lifecycleOperationJob?.cancel()
        lifecycleOperationJob = null
        runtimeHealthJob?.cancel()
        runtimeHealthJob = null
        removeViews()
        characterPackRevisionJob?.cancel()
        characterPackRevisionJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        loadedCharacterPackRevision = null
        if (startId == null) stopSelf() else stopSelf(startId)
    }

    private suspend fun handleSurfaceFailure(
        failure: RuntimeException,
        requestGeneration: Long? = null,
        startId: Int? = null
    ) {
        if (
            requestGeneration != null &&
            !isServiceOperationActive(requestGeneration)
        ) return
        try {
            if (
                shouldDisableActivationAfterSurfaceFailure(
                    failure = failure,
                    overlayPermissionGranted = Settings.canDrawOverlays(this)
                )
            ) {
                disableActivationBestEffort()
            }
        } finally {
            stopFloating(requestGeneration = requestGeneration, startId = startId)
        }
    }

    private suspend fun canUseFloatingReview(): Boolean {
        return resolveMembershipFeatureAccessUseCase(MembershipFeature.FLOATING_REVIEW) ==
            MembershipFeatureAccess.ALLOWED
    }

    private suspend fun disableActivationBestEffort() {
        try {
            floatingActivationCoordinator.disableRunningStatePreservingRequest()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Surface teardown still takes priority over a persistence/reporting failure.
        }
    }

    private fun startCharacterPackRevisionMonitoring(operationGeneration: Long) {
        characterPackRevisionJob?.cancel()
        characterPackRevisionJob = serviceScope.launch {
            try {
                characterPackRepository.observeInstalled().collect { installed ->
                    if (!isServiceOperationActive(operationGeneration)) return@collect
                    val selectedPackId = currentSettings.selectedCharacterPackId ?: return@collect
                    if (
                        ballView?.isAttachedToWindow != true ||
                        cardView?.isAttachedToWindow != true
                    ) {
                        return@collect
                    }
                    reloadInstalledCharacterPackIfNeeded(
                        packId = selectedPackId,
                        installed = installed[selectedPackId]
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Runtime health monitoring remains active and retries a missed reload safely.
            }
        }
    }

    private suspend fun reloadInstalledCharacterPackIfNeeded(
        packId: String,
        installed: InstalledCharacterPack? = null
    ) {
        val installedRevision = installed ?: try {
            characterPackRepository.getInstalled(packId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return
        val revision = installedRevision.toCharacterPackRevision()
        if (revision == loadedCharacterPackRevision) return
        loadAndFinalizeCharacterPack(packId, installedRevision)
    }

    private suspend fun loadAndFinalizeCharacterPack(
        packId: String,
        installed: InstalledCharacterPack?
    ): SpritePackId? {
        val loadedPackId = floatingPetController.forceReloadPackAndAwait(SpritePackId(packId))
        val targetReady = loadedPackId?.value == packId
        if (installed == null) return loadedPackId

        if (!installed.pendingRuntimeValidation) {
            if (targetReady) {
                loadedCharacterPackRevision = installed.toCharacterPackRevision()
            }
            return loadedPackId
        }

        if (targetReady) {
            val acknowledged = try {
                characterPackRepository.acknowledgeRuntimeReady(
                    packId = installed.packId,
                    packVersion = installed.packVersion,
                    installedDirectory = installed.installedDirectory
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (acknowledged) {
                loadedCharacterPackRevision = installed.toCharacterPackRevision()
                return loadedPackId
            }
            return null
        }

        val rolledBack = try {
            characterPackRepository.rollbackPendingRuntimeValidation(
                packId = installed.packId,
                packVersion = installed.packVersion,
                installedDirectory = installed.installedDirectory
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (rolledBack) {
            loadedCharacterPackRevision = currentCharacterPackRevision(packId)
        }
        // The controller may already be displaying LKG, but the requested revision failed.
        return null
    }

    private suspend fun currentCharacterPackRevision(
        packId: String
    ): CharacterPackInstallRevision? {
        return try {
            characterPackRepository.getInstalled(packId)?.toCharacterPackRevision()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun InstalledCharacterPack.toCharacterPackRevision(): CharacterPackInstallRevision =
        CharacterPackInstallRevision(
            packId = packId,
            packVersion = packVersion,
            installedDirectory = installedDirectory
        )

    private fun startRuntimeHealthMonitoring(operationGeneration: Long) {
        runtimeHealthJob?.cancel()
        runtimeHealthJob = serviceScope.launch {
            while (isServiceOperationActive(operationGeneration)) {
                delay(RUNTIME_HEALTH_CHECK_INTERVAL_MS)
                if (!isServiceOperationActive(operationGeneration)) return@launch
                val healthy = try {
                    isRuntimeHealthy(operationGeneration)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (!isServiceOperationActive(operationGeneration)) return@launch
                if (!healthy) {
                    stopFloating(requestGeneration = operationGeneration)
                    return@launch
                }
            }
        }
    }

    private suspend fun isRuntimeHealthy(operationGeneration: Long): Boolean {
        if (!isServiceOperationActive(operationGeneration)) return true
        val previousPackId = currentSettings.selectedCharacterPackId
        val settings = floatingWordController.getSettings()
        if (!isServiceOperationActive(operationGeneration)) return true
        updateCurrentSettings(settings)
        if (
            !settings.enabled &&
            runMode != FloatingServiceRunMode.TEMPORARY_PREVIEW
        ) {
            return false
        }
        val selectedPackId = settings.selectedCharacterPackId?.takeIf { it.isNotBlank() }
        if (selectedPackId == null) {
            disableActivationBestEffort()
            return false
        }

        val overlayPermissionGranted = Settings.canDrawOverlays(this)
        val membershipAllowed = canUseFloatingReview()
        if (!isServiceOperationActive(operationGeneration)) return true
        if (!overlayPermissionGranted || !membershipAllowed) {
            disableActivationBestEffort()
            return false
        }
        val characterPackUsable = floatingActivationCoordinator.isCurrentPackUsable()
        if (!isServiceOperationActive(operationGeneration)) return true
        val snapshot = FloatingServiceHealthSnapshot(
            settingsEnabled = settings.enabled,
            overlayPermissionGranted = overlayPermissionGranted,
            membershipAllowed = membershipAllowed,
            characterPackUsable = characterPackUsable
        )
        if (!shouldKeepFloatingServiceRunning(snapshot, runMode)) {
            if (!characterPackUsable) disableActivationBestEffort()
            return false
        }
        if (
            runMode == FloatingServiceRunMode.TEMPORARY_PREVIEW &&
            settings.enabled
        ) {
            runMode = FloatingServiceRunMode.ENABLED
        }
        if (
            runMode == FloatingServiceRunMode.ENABLED &&
            ballView?.isAttachedToWindow == true &&
            cardView?.isAttachedToWindow == true
        ) {
            if (previousPackId != settings.selectedCharacterPackId) {
                loadedCharacterPackRevision = null
            }
            reloadInstalledCharacterPackIfNeeded(selectedPackId)
        }
        applyFloatingAppearance()
        if (!isDragging) reconcileBallPosition(persistIfNeeded = false)
        return true
    }

    private fun reportFloatingStartedIfNeeded(activationRequestId: String?) {
        if (
            shouldReplaceFloatingStartedReport(
                reportInProgress = floatingStartedReportJob?.isActive == true,
                activeRequestId = floatingStartedReportRequestId,
                incomingRequestId = activationRequestId
            )
        ) {
            floatingStartedReportJob?.cancel()
            floatingStartedReportJob = null
            floatingStartedReportRequestId = null
        }
        if (!shouldReportFloatingStarted(
                alreadyReported = hasReportedFloatingStarted && activationRequestId == null,
                reportInProgress = floatingStartedReportJob?.isActive == true,
                runMode = runMode,
                ballViewAttached = ballView?.isAttachedToWindow == true,
                cardViewAttached = cardView?.isAttachedToWindow == true
            )
        ) return

        val startedPackId = currentSettings.selectedCharacterPackId
            ?.takeIf { it.isNotBlank() } ?: return
        val reportSurfaceGeneration = floatingSurfaceGeneration
        val reportAttempt = ++floatingStartedReportAttempt
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                val didReport = if (activationRequestId != null) {
                    floatingActivationCoordinator.completeActivationOnFloatingStarted(
                        packId = startedPackId,
                        expectedRequestId = activationRequestId
                    )
                } else {
                    reportUncorrelatedFloatingStarted(startedPackId)
                }
                if (didReport && floatingSurfaceGeneration == reportSurfaceGeneration) {
                    hasReportedFloatingStarted = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A committed request stays pending and can be finalized by a later foreground start.
            } finally {
                if (
                    floatingSurfaceGeneration == reportSurfaceGeneration &&
                    floatingStartedReportAttempt == reportAttempt
                ) {
                    floatingStartedReportJob = null
                    floatingStartedReportRequestId = null
                }
            }
        }
        floatingStartedReportJob = job
        floatingStartedReportRequestId = activationRequestId
        job.start()
    }

    private fun reportUncorrelatedFloatingStarted(packId: String): Boolean {
        return runCatching {
            floatingActivationEventReporter.report(
                FloatingActivationEvent.FLOATING_STARTED,
                mapOf("packId" to packId)
            )
        }.isSuccess
    }

    private suspend fun applyBallAppearanceIfAttached(
        operationGeneration: Long,
        startId: Int
    ) {
        if (!isServiceOperationActive(operationGeneration)) return
        if (ballView?.isAttachedToWindow != true || ballParams == null) {
            if (lifecycleOperationJob?.isActive != true) {
                // This appearance-only start may already have a newer appearance request behind it.
                // Stop only when its startId is still current; do not invalidate the newer request.
                stopSelf(startId)
            }
            return
        }
        resolveLatestSettings()
        if (!Settings.canDrawOverlays(this)) {
            disableActivationBestEffort()
            stopFloating(requestGeneration = operationGeneration)
            return
        }
        val currentPackUsable = floatingActivationCoordinator.isCurrentPackUsable()
        if (!isServiceOperationActive(operationGeneration)) return
        if (!currentPackUsable) {
            disableActivationBestEffort()
            stopFloating(requestGeneration = operationGeneration)
            return
        }
        if (!isServiceOperationActive(operationGeneration)) return
        applyBallSize()
        applyBallOpacity()
        reconcileBallPosition(persistIfNeeded = false)
    }

    private suspend fun applyCharacterPack(
        requestGeneration: Long,
        requestedPackId: String?,
        downloadRequestId: String?
    ) {
        if (!isServiceOperationActive(requestGeneration)) return
        val settings = resolveLatestSettings()
        if (!isServiceOperationActive(requestGeneration)) return
        val selectedPackId = settings.selectedCharacterPackId?.takeIf { it.isNotBlank() }
        if (selectedPackId == null) {
            disableActivationBestEffort()
            stopFloating(requestGeneration = requestGeneration)
            return
        }
        val installed = try {
            characterPackRepository.getInstalled(selectedPackId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val loadedPackId = loadAndFinalizeCharacterPack(selectedPackId, installed)
        if (!isServiceOperationActive(requestGeneration)) return
        val latestSelectedPackId = resolveLatestSettings().selectedCharacterPackId
        if (
            !shouldAcknowledgeManagementPackReload(
                requestedPackId = requestedPackId,
                downloadRequestId = downloadRequestId,
                selectedPackId = latestSelectedPackId,
                loadedPackId = loadedPackId
            )
        ) return
        try {
            characterPackRepository.acknowledgeManagementDownloadCompletion(
                packId = checkNotNull(requestedPackId),
                downloadRequestId = checkNotNull(downloadRequestId)
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the completion marker so a later visible management page can retry safely.
        }
    }

    private fun isServiceOperationActive(operationGeneration: Long): Boolean {
        return isFloatingServiceOperationActive(
            stopping = stopping,
            currentGeneration = this.operationGeneration,
            operationGeneration = operationGeneration
        )
    }

    private fun refreshWords(showNext: Boolean) {
        cancelPendingCardLoad()
        wordRefreshJob?.cancel()
        wordSequenceRefreshPending = true
        val generation = ++wordRefreshGeneration
        wordRefreshJob = serviceScope.launch {
            try {
                val settings = resolveLatestSettings()
                var loadedWords = withContext(Dispatchers.IO) {
                    floatingWordController.loadWords(settings)
                }
                if (settings.orderType == FloatingWordOrderType.RANDOM) {
                    loadedWords = loadedWords.shuffled()
                }
                if (generation != wordRefreshGeneration) return@launch
                val sequenceKey = settings.wordSequenceKey()
                if (currentSettings.wordSequenceKey() != sequenceKey) return@launch
                invalidatePreparedNextCard()
                words = loadedWords
                currentIndex = 0
                loadedWordSequenceKey = sequenceKey
                wordSequenceRefreshPending = false
                if (showNext) {
                    showNextWord()
                } else {
                    scheduleHiddenCardPrefetch()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A refresh failure keeps the last successfully loaded word sequence.
            } finally {
                if (generation == wordRefreshGeneration) wordRefreshJob = null
            }
        }
    }

    private fun ensureViews(packId: SpritePackId) {
        if (
            ballView?.isAttachedToWindow == true &&
            cardView?.isAttachedToWindow == true
        ) return
        if (ballView != null || cardView != null) removeViews()

        val inflater = LayoutInflater.from(this)
        val newBallView = inflater.inflate(R.layout.module_floating_review_view_floating_ball, null)
        val newCardView = inflater.inflate(R.layout.module_floating_review_view_floating_card, null).apply {
            visibility = View.GONE
        }
        val newBallParams = createBallLayoutParams()
        val newCardParams = createCardLayoutParams()
        var cardAttached = false
        var ballAttached = false

        try {
            windowManager.addView(newCardView, newCardParams)
            cardAttached = true
            windowManager.addView(newBallView, newBallParams)
            ballAttached = true

            ballView = newBallView
            cardView = newCardView
            ballParams = newBallParams
            cardParams = newCardParams

            val renderHost = newBallView as? FloatingPetRenderHost
                ?: error("Floating ball layout must use FloatingPetRenderHost as its root")
            floatingPetController.attach(
                renderHost,
                packId
            )

            applyFloatingAppearance()
            configureBallGestures()
            bindBallDrag()
            bindCardActions()
            floatingSurfaceGeneration++
        } catch (failure: RuntimeException) {
            runCatching { floatingPetController.detach() }
            if (ballAttached || newBallView.isAttachedToWindow) {
                removeWindowViewSafely(newBallView)
            }
            if (cardAttached || newCardView.isAttachedToWindow) {
                removeWindowViewSafely(newCardView)
            }
            ballView = null
            cardView = null
            ballParams = null
            cardParams = null
            lastMovementBounds = null
            cachedCardWidth = 0
            cachedCardHeight = 0
            ballGestureDetector = null
            throw failure
        }
    }

    private fun reconcileBallPosition(persistIfNeeded: Boolean) {
        val params = ballParams ?: return
        val movementBounds = getMovementBounds(currentSettings)
        val position = resolveBallPositionForSettings(
            settings = currentSettings,
            bounds = movementBounds,
            previousBounds = lastMovementBounds,
            dockManager = dockManager
        )
        val shouldPersist = persistIfNeeded && needsPersistence(position)
        val resolvedDockState = currentSettings.dockState?.normalized(currentSettings.dockConfig)
        val positionChanged = params.x != position.x || params.y != position.y
        params.x = position.x
        params.y = position.y
        if (positionChanged) {
            ballView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        }
        updateLocalBallState(position, resolvedDockState)
        lastMovementBounds = movementBounds
        if (isCardVisible()) {
            updateFloatingSpeechLayout()
        }
        if (shouldPersist) {
            persistBallPosition(position, resolvedDockState)
        }
    }

    private fun removeViews() {
        floatingSurfaceGeneration++
        floatingStartedReportAttempt++
        floatingStartedReportJob?.cancel()
        floatingStartedReportJob = null
        floatingStartedReportRequestId = null
        hasReportedFloatingStarted = false
        previewAutoHideJob?.cancel()
        previewAutoHideJob = null
        previewAutoHidePending = false
        cardRequestedVisible = false
        cancelPendingCardLoad()
        wordRefreshGeneration++
        wordRefreshJob?.cancel()
        wordRefreshJob = null
        wordSequenceRefreshPending = true
        notificationUpdateGeneration++
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
        pendingNotificationContent = null

        invalidatePreparedNextCard()
        val oldBallView = ballView
        val oldCardView = cardView
        ballView = null
        cardView = null
        ballParams = null
        cardParams = null
        lastMovementBounds = null
        cachedCardWidth = 0
        cachedCardHeight = 0
        ballGestureDetector = null
        isDragging = false
        lastBallTapEventTimeMillis = null
        runCatching { floatingPetController.detach() }
        oldBallView?.let(::removeWindowViewSafely)
        oldCardView?.let(::removeWindowViewSafely)
    }

    private fun removeWindowViewSafely(view: View) {
        if (runCatching { windowManager.removeViewImmediate(view) }.isFailure) {
            runCatching { windowManager.removeView(view) }
        }
    }

    private fun createBallLayoutParams(): WindowManager.LayoutParams {
        val (petWidth, petHeight) = getPetWindowSize()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            petWidth,
            petHeight,
            type,
            floatingBallWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    private fun createCardLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            floatingCardWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun configureBallGestures() {
        lastBallTapEventTimeMillis = null
        ballGestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (!isDragging) {
                        val suppressTap = isRapidRepeatTap(
                            previousEventTimeMillis = lastBallTapEventTimeMillis,
                            eventTimeMillis = e.eventTime,
                            suppressionWindowMillis = ViewConfiguration.getDoubleTapTimeout().toLong()
                        )
                        if (!suppressTap) {
                            lastBallTapEventTimeMillis = e.eventTime
                            handleBallSingleTap()
                        }
                    }
                    return true
                }
            }
        )
    }

    private fun bindBallDrag() {
        val threshold = 6.dpToPx(this).toFloat()
        ballView?.setOnTouchListener { _, event ->
            val params = ballParams ?: return@setOnTouchListener false
            val gestureDetector = ballGestureDetector
            gestureDetector?.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    dragStartBallX = params.x
                    dragStartBallY = params.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchDownX
                    val dy = event.rawY - touchDownY
                    var dragJustStarted = false
                    if (!isDragging && (abs(dx) > threshold || abs(dy) > threshold)) {
                        isDragging = true
                        dragJustStarted = true
                        updateDraggedBallPosition(params, dx, dy)
                        clearLocalDockState()
                        floatingPetController.playEvent(PetEvent.DRAG_STARTED)
                    }
                    if (isDragging && !dragJustStarted) {
                        updateDraggedBallPosition(params, dx, dy)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        settleDraggedBall()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun updateDraggedBallPosition(
        params: WindowManager.LayoutParams,
        dx: Float,
        dy: Float
    ): Boolean {
        params.x = dragStartBallX + dx.roundToInt()
        params.y = dragStartBallY + dy.roundToInt()
        ballView?.let { windowManager.updateViewLayout(it, params) }
        if (isCardVisible()) updateFloatingSpeechLayout()
        return true
    }

    private fun clearLocalDockState() {
        if (currentSettings.dockState != null) {
            updateCurrentSettings(currentSettings.copy(dockState = null))
        }
    }

    private fun settleDraggedBall() {
        val params = ballParams ?: return
        val result = dockManager.resolveFreeRestingState(
            bounds = getMovementBounds(currentSettings),
            x = params.x,
            y = params.y
        )
        isDragging = false
        applyBallPosition(result.position)
        persistBallPosition(result.position, result.dockState)
        floatingPetController.playEvent(PetEvent.DRAG_ENDED)
    }

    private fun handleBallSingleTap() {
        when (resolveSingleTapAction(cardRequestedVisible, currentWord != null)) {
            FloatingBallSingleTapAction.ShowCard -> showCard()

            FloatingBallSingleTapAction.ShowNextCard -> {
                val keepCurrentCardPosition = isCardVisible()
                showCard()
                showNextWord(keepCurrentCardPosition)
            }

            FloatingBallSingleTapAction.HideCard -> {
                hideCard()
            }
        }
        floatingPetController.playEvent(PetEvent.PET_TAP)
    }

    private fun applyBallPosition(position: FloatingBallPosition) {
        val params = ballParams ?: return
        params.x = position.x
        params.y = position.y
        ballView?.let { windowManager.updateViewLayout(it, params) }
        if (isCardVisible()) updateFloatingSpeechLayout()
    }

    private fun bindCardActions() {
        cardView?.findViewById<View>(R.id.module_floating_review_btn_favorite)?.setOnClickListener {
            toggleCurrentFavorite()
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_refresh)?.setOnClickListener {
            showNextWord()
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_copy)?.setOnClickListener {
            copyCurrentWord()
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_close)?.setOnClickListener {
            when (resolveCardCloseAction()) {
                FloatingCardCloseAction.HideCard -> hideCard()
            }
        }
    }

    private fun hideCard() {
        val wasVisible = isCardVisible()
        val wasRequestedVisible = cardRequestedVisible
        cardRequestedVisible = false
        cancelPendingCardLoad()
        previewAutoHideJob?.cancel()
        previewAutoHideJob = null
        previewAutoHidePending = false
        cardView?.visibility = View.GONE
        if (wasVisible || wasRequestedVisible) floatingPetController.setCardVisible(false)
        scheduleHiddenCardPrefetch()
    }

    private fun scheduleHiddenCardPrefetch() {
        if (
            cardRequestedVisible ||
            isCardVisible() ||
            cardView == null ||
            words.isEmpty()
        ) return
        hiddenCardPrefetchGeneration++
        hiddenCardPrefetchJob?.cancel()
        val generation = hiddenCardPrefetchGeneration
        val sourceWords = words
        val sourceIndex = currentIndex
        val sourceSettings = currentSettings
        val sourceSettingsRevision = settingsRevision
        val sourceKey = sourceSettings.wordSequenceKey()
        hiddenCardPrefetchJob = serviceScope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    val next = advanceFloatingWordSequence(
                        words = sourceWords,
                        currentIndex = sourceIndex,
                        orderType = sourceSettings.orderType
                    )
                    val word = next.word ?: return@withContext null
                    val content = floatingWordController.loadCardContent(word, sourceSettings)
                    PreparedNextCard(
                        sourceWords = sourceWords,
                        sourceIndex = sourceIndex,
                        sourceSettingsRevision = sourceSettingsRevision,
                        sourceKey = sourceKey,
                        result = next,
                        content = content
                    )
                } ?: return@launch
                if (
                    generation != hiddenCardPrefetchGeneration ||
                    cardRequestedVisible ||
                    isCardVisible() ||
                    words !== sourceWords ||
                    currentIndex != sourceIndex ||
                    settingsRevision != sourceSettingsRevision ||
                    currentSettings.wordSequenceKey() != sourceKey ||
                    currentSettings.fieldConfigs != sourceSettings.fieldConfigs
                ) return@launch
                val word = checkNotNull(prepared.result.word)
                val preparedRender = createPreparedCardRender(
                    word = word,
                    definitions = prepared.content.definitions,
                    examples = prepared.content.examples,
                    settings = sourceSettings
                )
                if (
                    generation == hiddenCardPrefetchGeneration &&
                    !cardRequestedVisible &&
                    !isCardVisible()
                ) {
                    preparedNextCard = prepared.copy(render = preparedRender)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Hidden prefetch is best effort; the normal card load remains authoritative.
            } finally {
                if (generation == hiddenCardPrefetchGeneration) {
                    hiddenCardPrefetchJob = null
                }
            }
        }
    }

    private fun consumePreparedNextCard(
        settings: FloatingWordSettings,
        sequenceKey: WordSequenceKey,
        keepCurrentCardPosition: Boolean
    ): Boolean {
        val prepared = preparedNextCard ?: return false
        if (
            prepared.sourceWords !== words ||
            prepared.sourceIndex != currentIndex ||
            prepared.sourceSettingsRevision != settingsRevision ||
            prepared.sourceKey != sequenceKey ||
            currentSettings.fieldConfigs != settings.fieldConfigs
        ) {
            preparedNextCard = null
            return false
        }
        preparedNextCard = null
        val word = prepared.result.word ?: return false
        val wordChanged = currentWord?.id != word.id
        words = prepared.result.words
        currentIndex = prepared.result.nextIndex
        currentWord = word
        val usedPreparedRender = prepared.render?.let { render ->
            applyPreparedCardRender(
                prepared = render,
                word = word,
                definitions = prepared.content.definitions
            )
        } == true
        if (!usedPreparedRender) {
            renderCard(
                word = word,
                definitions = prepared.content.definitions,
                examples = prepared.content.examples,
                settings = settings
            )
        }
        loadedWordSequenceKey = sequenceKey
        wordSequenceRefreshPending = false
        updateNotification(word.word)
        serviceScope.launch(Dispatchers.IO) {
            runCatching { floatingWordController.recordDisplay(word.id) }
                .onFailure { if (it is CancellationException) throw it }
        }
        if (wordChanged) floatingPetController.playEvent(PetEvent.WORD_CHANGED)
        showCardAfterRefresh(keepCurrentCardPosition)
        return true
    }

    private fun cancelHiddenCardPrefetch(clearPrepared: Boolean) {
        hiddenCardPrefetchGeneration++
        hiddenCardPrefetchJob?.cancel()
        hiddenCardPrefetchJob = null
        if (clearPrepared) preparedNextCard = null
    }

    private fun invalidatePreparedNextCard() {
        cancelHiddenCardPrefetch(clearPrepared = true)
    }

    private fun createPreparedCardRender(
        word: Word,
        definitions: List<WordDefinitions>,
        examples: List<WordExample>,
        settings: FloatingWordSettings
    ): PreparedCardRender? {
        val attachedCard = cardView ?: return null
        val preparedRoot = LayoutInflater.from(this).inflate(
            R.layout.module_floating_review_view_floating_card,
            null
        )
        copyCardPanelMeasurementState(attachedCard, preparedRoot)
        val hasWordContent = renderCardInto(
            target = preparedRoot,
            word = word,
            definitions = definitions,
            examples = examples,
            settings = settings
        )
        val maxWidth = resources.getDimensionPixelSize(R.dimen.module_floating_review_card_width)
        preparedRoot.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val measuredWidth = preparedRoot.measuredWidth
        val measuredHeight = preparedRoot.measuredHeight
        if (measuredWidth <= 0 || measuredHeight <= 0) return null
        return PreparedCardRender(
            root = preparedRoot,
            measuredWidth = measuredWidth,
            measuredHeight = measuredHeight,
            hasWordContent = hasWordContent
        )
    }

    private fun copyCardPanelMeasurementState(sourceRoot: View, targetRoot: View) {
        val sourceParams = sourceRoot
            .findViewById<View>(R.id.module_floating_review_card_panel)
            ?.layoutParams as? FrameLayout.LayoutParams ?: return
        val targetPanel = targetRoot.findViewById<View>(R.id.module_floating_review_card_panel)
            ?: return
        val targetParams = targetPanel.layoutParams as? FrameLayout.LayoutParams ?: return
        targetParams.topMargin = sourceParams.topMargin
        targetParams.bottomMargin = sourceParams.bottomMargin
        targetPanel.layoutParams = targetParams
    }

    private fun applyPreparedCardRender(
        prepared: PreparedCardRender,
        word: Word,
        definitions: List<WordDefinitions>
    ): Boolean {
        cardView?.let { copyCardPanelMeasurementState(it, prepared.root) }
        val targetRoot = cardView as? ViewGroup ?: return false
        val sourceRoot = prepared.root as? ViewGroup ?: return false
        val targetPanel = targetRoot.findViewById<View>(R.id.module_floating_review_card_panel)
            ?: return false
        val sourcePanel = sourceRoot.findViewById<View>(R.id.module_floating_review_card_panel)
            ?: return false
        val targetParent = targetPanel.parent as? ViewGroup ?: return false
        val sourceParent = sourcePanel.parent as? ViewGroup ?: return false
        val targetIndex = targetParent.indexOfChild(targetPanel)
        if (targetIndex < 0) return false
        sourceParent.removeView(sourcePanel)
        targetParent.removeViewAt(targetIndex)
        targetParent.addView(sourcePanel, targetIndex)
        currentDefinitions = if (prepared.hasWordContent) definitions else emptyList()
        cachedCardWidth = prepared.measuredWidth
        cachedCardHeight = prepared.measuredHeight
        bindCardActions()
        if (prepared.hasWordContent) refreshFavoriteState(word)
        return true
    }

    private fun previewCard() {
        invalidatePreparedNextCard()
        val keepCurrentCardPosition = isCardVisible()
        val shouldAutoHideAfterPreview = !cardRequestedVisible ||
            previewAutoHidePending ||
            previewAutoHideJob != null
        previewAutoHidePending = shouldAutoHideAfterPreview
        previewAutoHideJob?.cancel()
        previewAutoHideJob = null
        if (currentWord == null) renderLoadingCard()
        showCard()
        beginCardLoad(
            onFailure = {
                if (currentWord == null) renderEmptyCard()
                showCardAfterRefresh(keepCurrentCardPosition)
                if (shouldAutoHideAfterPreview) schedulePreviewAutoHide()
            }
        ) { generation ->
            val settings = resolveLatestSettings()
            applyFloatingAppearance()
            val sequenceKey = settings.wordSequenceKey()
            var candidateWords = words
            var candidateIndex = currentIndex
            var candidateWord = currentWord
            var advancedSequence = false
            var reloadedWords = false
            if (candidateWord == null) {
                if (
                    candidateWords.isEmpty() ||
                    wordSequenceRefreshPending ||
                    loadedWordSequenceKey != sequenceKey
                ) {
                    candidateWords = floatingWordController.loadWords(settings)
                    if (settings.orderType == FloatingWordOrderType.RANDOM) {
                        candidateWords = candidateWords.shuffled()
                    }
                    candidateIndex = 0
                    reloadedWords = true
                }
                val preview = advanceFloatingWordSequence(
                    words = candidateWords,
                    currentIndex = candidateIndex,
                    orderType = settings.orderType
                )
                candidateWords = preview.words
                candidateIndex = preview.nextIndex
                candidateWord = preview.word
                advancedSequence = true
            }
            val content = candidateWord?.let {
                floatingWordController.loadCardContent(it, settings)
            }
            if (!isCurrentCardRequest(generation)) return@beginCardLoad
            if (
                currentSettings.fieldConfigs != settings.fieldConfigs ||
                (advancedSequence && currentSettings.wordSequenceKey() != sequenceKey)
            ) {
                if (shouldAutoHideAfterPreview) schedulePreviewAutoHide()
                return@beginCardLoad
            }
            val wordChanged = advancedSequence &&
                candidateWord != null &&
                currentWord?.id != candidateWord.id
            if (advancedSequence) {
                words = candidateWords
                currentIndex = candidateIndex
                currentWord = candidateWord
            }
            if (reloadedWords) {
                loadedWordSequenceKey = sequenceKey
                wordSequenceRefreshPending = false
            }
            val renderSettings = currentSettings
            if (candidateWord == null || content == null) {
                renderEmptyCard()
            } else {
                renderCard(candidateWord, content.definitions, content.examples, renderSettings)
                updateNotification(candidateWord.word)
            }
            if (wordChanged) {
                floatingPetController.playEvent(PetEvent.WORD_CHANGED)
            }
            showCardAfterRefresh(keepCurrentCardPosition)
            if (shouldAutoHideAfterPreview) {
                schedulePreviewAutoHide()
            }
        }
    }

    private fun schedulePreviewAutoHide() {
        previewAutoHideJob?.cancel()
        previewAutoHidePending = true
        previewAutoHideJob = serviceScope.launch {
            delay(PREVIEW_AUTO_HIDE_DELAY_MS)
            previewAutoHideJob = null
            previewAutoHidePending = false
            hideCard()
        }
    }

    private fun showNextWord(keepCurrentCardPosition: Boolean = isCardVisible()) {
        previewAutoHideJob?.cancel()
        previewAutoHideJob = null
        previewAutoHidePending = false
        if (currentWord == null) renderLoadingCard()
        if (!cardRequestedVisible) showCard()
        beginCardLoad(
            onFailure = {
                if (currentWord == null) renderEmptyCard()
                showCardAfterRefresh(keepCurrentCardPosition)
            }
        ) { generation ->
            val settings = resolveLatestSettings()
            applyFloatingAppearance()
            val sequenceKey = settings.wordSequenceKey()
            if (consumePreparedNextCard(settings, sequenceKey, keepCurrentCardPosition)) {
                return@beginCardLoad
            }
            var candidateWords = words
            var candidateIndex = currentIndex
            var reloadedWords = false
            if (
                candidateWords.isEmpty() ||
                wordSequenceRefreshPending ||
                loadedWordSequenceKey != sequenceKey
            ) {
                candidateWords = withContext(Dispatchers.IO) {
                    floatingWordController.loadWords(settings)
                }
                if (settings.orderType == FloatingWordOrderType.RANDOM) {
                    candidateWords = candidateWords.shuffled()
                }
                candidateIndex = 0
                reloadedWords = true
            }
            val nextWord = advanceFloatingWordSequence(
                candidateWords,
                candidateIndex,
                settings.orderType
            )
            val word = nextWord.word
            val content = word?.let {
                withContext(Dispatchers.IO) { floatingWordController.loadCardContent(it, settings) }
            }
            if (!isCurrentCardRequest(generation)) return@beginCardLoad
            if (
                currentSettings.wordSequenceKey() != sequenceKey ||
                currentSettings.fieldConfigs != settings.fieldConfigs
            ) return@beginCardLoad
            val wordChanged = word != null && currentWord?.id != word.id
            words = nextWord.words
            currentIndex = nextWord.nextIndex
            currentWord = word
            if (reloadedWords) {
                loadedWordSequenceKey = sequenceKey
                wordSequenceRefreshPending = false
            }
            val renderSettings = currentSettings
            if (word == null || content == null) {
                renderEmptyCard()
            } else {
                renderCard(word, content.definitions, content.examples, renderSettings)
                updateNotification(word.word)
                serviceScope.launch(Dispatchers.IO) {
                    runCatching { floatingWordController.recordDisplay(word.id) }
                        .onFailure { if (it is CancellationException) throw it }
                }
            }
            if (wordChanged) {
                floatingPetController.playEvent(PetEvent.WORD_CHANGED)
            }
            showCardAfterRefresh(keepCurrentCardPosition)
        }
    }

    private fun showCard() {
        val wasVisible = isCardVisible()
        cardRequestedVisible = true
        cancelHiddenCardPrefetch(clearPrepared = false)
        val card = cardView
        card?.visibility = View.VISIBLE
        if (!wasVisible) floatingPetController.setCardVisible(true)
        card?.post {
            if (cardRequestedVisible && cardView === card) {
                reconcileBallPosition(persistIfNeeded = false)
            }
        }
    }

    private fun showCardAfterRefresh(keepCurrentCardPosition: Boolean) {
        if (!cardRequestedVisible) return
        val card = cardView ?: return
        card.visibility = View.VISIBLE
        if (keepCurrentCardPosition) {
            card.requestLayout()
        } else {
            updateFloatingSpeechLayout()
        }
    }

    private fun beginCardLoad(
        onFailure: (Throwable) -> Unit,
        block: suspend (generation: Long) -> Unit
    ) {
        wordRefreshGeneration++
        wordRefreshJob?.cancel()
        wordRefreshJob = null
        cardLoadJob?.cancel()
        val generation = ++cardRequestGeneration
        setCardLoadInProgress(true)
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                yield()
                block(generation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isCurrentCardRequest(generation)) onFailure(error)
            } finally {
                if (generation == cardRequestGeneration) {
                    cardLoadJob = null
                    setCardLoadInProgress(false)
                }
            }
        }
        cardLoadJob = job
        job.start()
    }

    private fun cancelPendingCardLoad() {
        cardRequestGeneration++
        cardLoadJob?.cancel()
        cardLoadJob = null
        setCardLoadInProgress(false)
    }

    private fun isCurrentCardRequest(generation: Long): Boolean {
        return generation == cardRequestGeneration && cardRequestedVisible && cardView != null
    }

    private fun setCardLoadInProgress(loading: Boolean) {
        cardView?.findViewById<View>(R.id.module_floating_review_btn_refresh)?.apply {
            isEnabled = !loading && currentWord != null
            alpha = if (isEnabled) 1f else 0.38f
        }
    }

    private fun FloatingWordSettings.wordSequenceKey(): WordSequenceKey {
        return WordSequenceKey(
            sourceType = sourceType,
            orderType = orderType,
            selectedWordIds = selectedWordIds
        )
    }

    private data class WordSequenceKey(
        val sourceType: FloatingWordSourceType,
        val orderType: FloatingWordOrderType,
        val selectedWordIds: List<Long>
    )

    private data class PreparedNextCard(
        val sourceWords: List<Word>,
        val sourceIndex: Int,
        val sourceSettingsRevision: Long,
        val sourceKey: WordSequenceKey,
        val result: FloatingWordAdvanceResult,
        val content: FloatingWordCardContent,
        val render: PreparedCardRender? = null
    )

    private data class PreparedCardRender(
        val root: View,
        val measuredWidth: Int,
        val measuredHeight: Int,
        val hasWordContent: Boolean
    )

    private suspend fun resolveLatestSettings(): FloatingWordSettings {
        val requestRevision = settingsRevision
        val loaded = withContext(Dispatchers.IO) {
            floatingWordController.getSettings()
        }
        if (requestRevision == settingsRevision || currentSettings == loaded) {
            updateCurrentSettings(loaded)
        }
        return currentSettings
    }

    private fun updateCurrentSettings(settings: FloatingWordSettings) {
        if (currentSettings == settings) return
        val prefetchContractChanged =
            currentSettings.wordSequenceKey() != settings.wordSequenceKey() ||
                currentSettings.fieldConfigs != settings.fieldConfigs
        currentSettings = settings
        settingsRevision++
        if (prefetchContractChanged) {
            invalidatePreparedNextCard()
            if (!cardRequestedVisible && !isCardVisible()) scheduleHiddenCardPrefetch()
        }
    }

    private fun renderLoadingCard() {
        renderStatusCard(R.string.feature_floating_review_loading)
    }

    private fun renderEmptyCard() {
        renderStatusCard(R.string.module_floating_review_empty)
    }

    private fun renderStatusCard(messageRes: Int) {
        invalidateCardMeasurement()
        currentDefinitions = emptyList()
        cardView?.let { renderStatusCardInto(it, messageRes) }
    }

    private fun renderStatusCardInto(target: View, messageRes: Int) {
        target.findViewById<TextView>(R.id.module_floating_review_tv_word)?.apply {
            text = getString(messageRes)
            visibility = View.VISIBLE
        }
        target.findViewById<View>(R.id.module_floating_review_phonetic_row)?.visibility = View.GONE
        target.findViewById<View>(R.id.module_floating_review_phonetic_divider)?.visibility = View.GONE
        val container = target.findViewById<LinearLayout>(
            R.id.module_floating_review_floating_fields_container
        ) ?: return
        container.removeAllViews()
        applyCardActionState(resolveCardActionState(hasWord = false), target)
    }

    private fun renderCard(
        word: Word,
        definitions: List<WordDefinitions>,
        examples: List<WordExample>,
        settings: FloatingWordSettings
    ) {
        invalidateCardMeasurement()
        currentDefinitions = definitions
        val target = cardView ?: return
        val hasWordContent = renderCardInto(target, word, definitions, examples, settings)
        if (!hasWordContent) {
            currentDefinitions = emptyList()
            return
        }
        refreshFavoriteState(word)
    }

    private fun renderCardInto(
        target: View,
        word: Word,
        definitions: List<WordDefinitions>,
        examples: List<WordExample>,
        settings: FloatingWordSettings
    ): Boolean {
        val container = target.findViewById<LinearLayout>(
            R.id.module_floating_review_floating_fields_container
        ) ?: return false
        container.removeAllViews()
        val configs = settings.fieldConfigs.filter { it.enabled }
        if (configs.isEmpty()) {
            renderStatusCardInto(target, R.string.module_floating_review_empty)
            return false
        }
        val enabledTypes = configs.map { it.type }.toSet()
        renderHeader(target, word, enabledTypes)
        renderPhonetics(target, word, enabledTypes)
        renderDefinitions(container, definitions, enabledTypes, configs)
        renderExtraFields(container, word, definitions, examples, configs)
        applyCardActionState(resolveCardActionState(hasWord = true), target)
        return true
    }

    private fun applyCardActionState(state: FloatingCardActionState, target: View? = cardView) {
        target?.findViewById<View>(R.id.module_floating_review_btn_favorite)?.apply {
            isEnabled = state.favoriteEnabled
            alpha = if (state.favoriteEnabled) 1f else 0.38f
        }
        target?.findViewById<View>(R.id.module_floating_review_btn_refresh)?.apply {
            isEnabled = state.refreshEnabled
            alpha = if (state.refreshEnabled) 1f else 0.38f
        }
        target?.findViewById<View>(R.id.module_floating_review_btn_copy)?.apply {
            isEnabled = state.copyEnabled
            alpha = if (state.copyEnabled) 1f else 0.38f
        }
    }

    private fun renderHeader(target: View, word: Word, enabledTypes: Set<FloatingWordFieldType>) {
        target.findViewById<TextView>(R.id.module_floating_review_tv_word)?.apply {
            text = word.word
            visibility = if (FloatingWordFieldType.WORD in enabledTypes) View.VISIBLE else View.GONE
        }
    }

    private fun renderPhonetics(target: View, word: Word, enabledTypes: Set<FloatingWordFieldType>) {
        val row = target.findViewById<View>(R.id.module_floating_review_phonetic_row) ?: return
        val divider = target.findViewById<View>(R.id.module_floating_review_phonetic_divider)
        val uk = word.phoneticUK?.takeIf { it.isNotBlank() }
        val us = word.phoneticUS?.takeIf { it.isNotBlank() }
        val showRow = FloatingWordFieldType.PHONETIC in enabledTypes && (uk != null || us != null)
        row.visibility = if (showRow) View.VISIBLE else View.GONE
        divider?.visibility = if (showRow) View.VISIBLE else View.GONE
        if (!showRow) return

        bindPhoneticGroup(
            target = target,
            groupId = R.id.module_floating_review_phonetic_uk_group,
            textId = R.id.module_floating_review_tv_phonetic_uk,
            value = uk
        )
        bindPhoneticGroup(
            target = target,
            groupId = R.id.module_floating_review_phonetic_us_group,
            textId = R.id.module_floating_review_tv_phonetic_us,
            value = us
        )
    }

    private fun bindPhoneticGroup(target: View, groupId: Int, textId: Int, value: String?) {
        val group = target.findViewById<View>(groupId) ?: return
        group.visibility = if (value == null) View.GONE else View.VISIBLE
        target.findViewById<TextView>(textId)?.text = value.orEmpty()
    }

    private fun renderDefinitions(
        container: LinearLayout,
        definitions: List<WordDefinitions>,
        enabledTypes: Set<FloatingWordFieldType>,
        configs: List<FloatingWordFieldConfig>
    ) {
        val showMeaning = FloatingWordFieldType.MEANING in enabledTypes
        val showPartOfSpeech = FloatingWordFieldType.PART_OF_SPEECH in enabledTypes
        if (!showMeaning && !showPartOfSpeech) return

        val text = buildDefinitionLines(
            definitions = definitions,
            showPartOfSpeech = showPartOfSpeech || showMeaning,
            showMeaning = showMeaning
        )
        if (text.isBlank()) return
        val definitionTextSize = resolveFontSize(configs, FloatingWordFieldType.MEANING, 16)
            .coerceAtLeast(16)
        container.addView(
            buildTextView(
                text = text,
                textSizeSp = definitionTextSize.toFloat(),
                color = 0xFF111827.toInt(),
                bold = false
            ).apply {
                includeFontPadding = false
                setLineSpacing(10.dpToPx(this@FloatingWordService).toFloat(), 1f)
            }
        )
    }

    private fun renderExtraFields(
        container: LinearLayout,
        word: Word,
        definitions: List<WordDefinitions>,
        examples: List<WordExample>,
        configs: List<FloatingWordFieldConfig>
    ) {
        configs
            .filter { it.type in setOf(FloatingWordFieldType.EXAMPLE, FloatingWordFieldType.NOTE, FloatingWordFieldType.IMAGE) }
            .forEach { config ->
                val view = when (config.type) {
                    FloatingWordFieldType.EXAMPLE -> buildTextView(
                        buildExampleText(examples),
                        config.fontSizeSp.toFloat(),
                        0xFF334155.toInt(),
                        false
                    )

                    FloatingWordFieldType.NOTE -> buildTextView(
                        word.notes.orEmpty(),
                        config.fontSizeSp.toFloat(),
                        0xFF334155.toInt(),
                        false
                    )

                    FloatingWordFieldType.IMAGE -> buildImageView(word.mnemonicImageUrl, config.fontSizeSp)
                    else -> null
                }

                view?.takeIf { hasRenderableContent(it) }?.let {
                    val layoutParams = (it.layoutParams as? LinearLayout.LayoutParams)
                        ?: LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    layoutParams.topMargin = if (container.childCount > 0) 8.dpToPx(this) else 0
                    it.layoutParams = layoutParams
                    container.addView(it)
                }
            }
    }

    private fun buildTextView(
        text: String,
        textSizeSp: Float,
        color: Int,
        bold: Boolean
    ): TextView {
        val content = text.ifBlank { EMPTY_PLACEHOLDER }
        val isPlaceholder = content == EMPTY_PLACEHOLDER
        return TextView(this).apply {
            this.text = content
            setTextColor(if (isPlaceholder) 0xFF94A3B8.toInt() else color)
            this.textSize = textSizeSp
            includeFontPadding = false
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun buildImageView(url: String?, sizeDp: Int): View {
        if (url.isNullOrBlank()) {
            return buildTextView(EMPTY_PLACEHOLDER, 12f, 0xFF64748B.toInt(), false)
        }
        val height = (sizeDp.coerceAtLeast(80)).dpToPx(this)
        return ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(url)
        }
    }

    private fun hasRenderableContent(view: View): Boolean {
        return (view as? TextView)?.text?.toString() != EMPTY_PLACEHOLDER
    }

    private fun resolveFontSize(
        configs: List<FloatingWordFieldConfig>,
        type: FloatingWordFieldType,
        fallback: Int
    ): Int {
        return configs.firstOrNull { it.type == type }?.fontSizeSp ?: fallback
    }

    private fun buildDefinitionLines(
        definitions: List<WordDefinitions>,
        showPartOfSpeech: Boolean,
        showMeaning: Boolean
    ): String {
        if (definitions.isEmpty()) return ""
        return definitions.take(2).joinToString("\n") { definition ->
            when {
                showPartOfSpeech && showMeaning ->
                    "${formatPartOfSpeech(definition.partOfSpeech.abbr)} ${definition.meaningChinese}"
                showPartOfSpeech -> formatPartOfSpeech(definition.partOfSpeech.abbr)
                showMeaning -> definition.meaningChinese
                else -> ""
            }
        }
    }

    private fun formatPartOfSpeech(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return trimmed
        return if (trimmed.endsWith(".")) trimmed else "$trimmed."
    }

    private fun refreshFavoriteState(word: Word) {
        val button = cardView?.findViewById<ImageButton>(R.id.module_floating_review_btn_favorite)
        button?.setImageResource(R.drawable.module_floating_review_ic_star)
        button?.contentDescription = getString(R.string.module_floating_review_favorite)
        serviceScope.launch {
            runCatching { floatingWordController.isFavorite(word.id) }
                .onSuccess { favorite ->
                    if (currentWord?.id == word.id) applyFavoriteState(favorite)
                }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    private fun toggleCurrentFavorite() {
        val word = currentWord ?: return
        serviceScope.launch {
            runCatching {
                floatingWordController.toggleFavorite(word)
                floatingWordController.isFavorite(word.id)
            }.onSuccess { favorite ->
                if (currentWord?.id != word.id) return@onSuccess
                applyFavoriteState(favorite)
                floatingPetController.playEvent(
                    if (favorite) PetEvent.FAVORITE_ADDED else PetEvent.FAVORITE_REMOVED
                )
                Toast.makeText(
                    this@FloatingWordService,
                    getString(
                        if (favorite) {
                            R.string.module_floating_review_favorited
                        } else {
                            R.string.module_floating_review_unfavorited
                        }
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { if (it is CancellationException) throw it }
        }
    }

    private fun applyFavoriteState(favorite: Boolean) {
        cardView?.findViewById<ImageButton>(R.id.module_floating_review_btn_favorite)?.apply {
            setImageResource(
                if (favorite) {
                    R.drawable.module_floating_review_ic_star_filled
                } else {
                    R.drawable.module_floating_review_ic_star
                }
            )
            contentDescription = getString(
                if (favorite) {
                    R.string.module_floating_review_unfavorite
                } else {
                    R.string.module_floating_review_favorite
                }
            )
        }
    }

    private fun copyCurrentWord() {
        val word = currentWord ?: return
        val text = buildCopyText(word, currentDefinitions)
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.setPrimaryClip(
            ClipData.newPlainText(word.word, text)
        )
        Toast.makeText(
            this,
            getString(R.string.module_floating_review_copied),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun buildCopyText(
        word: Word,
        definitions: List<WordDefinitions>
    ): String {
        return buildList {
            add(word.word)
            buildPhoneticText(word).takeIf { it.isNotBlank() }?.let(::add)
            buildDefinitionLines(
                definitions = definitions,
                showPartOfSpeech = true,
                showMeaning = true
            ).takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString("\n")
    }

    private fun buildPhoneticText(word: Word): String {
        val us = word.phoneticUS?.takeIf { it.isNotBlank() }
        val uk = word.phoneticUK?.takeIf { it.isNotBlank() }
        return when {
            us != null && uk != null -> getString(
                R.string.module_floating_review_phonetic_both,
                us,
                uk
            )
            us != null -> getString(R.string.module_floating_review_phonetic_us_only, us)
            uk != null -> getString(R.string.module_floating_review_phonetic_uk_only, uk)
            else -> ""
        }
    }

    private fun buildMeaningText(definitions: List<WordDefinitions>): String {
        if (definitions.isEmpty()) return ""
        return definitions.take(2).joinToString("\n") { definition ->
            "${definition.partOfSpeech.abbr} ${definition.meaningChinese}"
        }
    }

    private fun buildPartOfSpeechText(definitions: List<WordDefinitions>): String {
        if (definitions.isEmpty()) return ""
        return definitions.map { it.partOfSpeech.abbr }.distinct().joinToString(" ")
    }

    private fun buildExampleText(examples: List<WordExample>): String {
        val example = examples.firstOrNull() ?: return ""
        val zh = example.chineseTranslation?.takeIf { it.isNotBlank() }
        return if (zh != null) "${example.englishSentence}\n$zh" else example.englishSentence
    }

    private fun updateFloatingSpeechLayout() {
        val params = cardParams ?: return
        val ball = ballParams ?: return
        val card = cardView ?: return
        val safeArea = getSafeDisplayRect()
        val maxWidth = resources.getDimensionPixelSize(R.dimen.module_floating_review_card_width)
        val (cardWidth, cardHeight) = measureCardForPosition(card, maxWidth)
        val (petWidth, petHeight) = getPetWindowSize()
        val tailSlotHeight = resources.getDimensionPixelSize(
            R.dimen.module_floating_review_tail_panel_offset
        )
        var layout = resolveFloatingSpeechLayout(
            safeArea = safeArea,
            ball = ball,
            petWidth = petWidth,
            petHeight = petHeight,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            tailSlotHeight = tailSlotHeight
        )
        if (applyFloatingSpeechTailLayout(layout)) {
            val (updatedCardWidth, updatedCardHeight) = measureCardForPosition(card, maxWidth)
            layout = resolveFloatingSpeechLayout(
                safeArea = safeArea,
                ball = ball,
                petWidth = petWidth,
                petHeight = petHeight,
                cardWidth = updatedCardWidth,
                cardHeight = updatedCardHeight,
                tailSlotHeight = tailSlotHeight
            )
            applyFloatingSpeechTailLayout(layout)
        }
        val shouldUpdateWindow = shouldUpdateFloatingCardWindow(
            currentX = params.x,
            currentY = params.y,
            targetX = layout.cardX,
            targetY = layout.cardY
        )
        params.x = layout.cardX
        params.y = layout.cardY
        if (shouldUpdateWindow) {
            runCatching { windowManager.updateViewLayout(card, params) }
        }
    }

    private fun resolveFloatingSpeechLayout(
        safeArea: Rect,
        ball: WindowManager.LayoutParams,
        petWidth: Int,
        petHeight: Int,
        cardWidth: Int,
        cardHeight: Int,
        tailSlotHeight: Int
    ): FloatingSpeechLayout {
        return speechLayoutEngine.resolve(
            safeArea = FloatingSpeechSafeArea(
                left = safeArea.left,
                top = safeArea.top,
                right = safeArea.right,
                bottom = safeArea.bottom
            ),
            petBounds = FloatingSpeechPetBounds(
                x = ball.x,
                y = ball.y,
                width = petWidth,
                height = petHeight
            ),
            cardSize = FloatingSpeechCardSize(
                width = cardWidth,
                height = cardHeight
            ),
            config = FloatingSpeechLayoutConfig(
                edgeMarginPx = resources.getDimensionPixelSize(
                    R.dimen.module_floating_review_card_edge_margin
                ),
                clearancePx = (currentSettings.cardGapDp).dpToPx(this),
                tailWidthPx = resources.getDimensionPixelSize(R.dimen.module_floating_review_tail_width),
                tailSafeInsetPx = resources.getDimensionPixelSize(
                    R.dimen.module_floating_review_tail_safe_inset
                ),
                tailSlotHeightPx = tailSlotHeight
            )
        )
    }

    private fun applyFloatingSpeechTailLayout(layout: FloatingSpeechLayout): Boolean {
        val card = cardView ?: return false
        val panel = card.findViewById<View>(R.id.module_floating_review_card_panel) ?: return false
        val tail = card.findViewById<FloatingSpeechTailView>(
            R.id.module_floating_review_card_tail
        ) ?: return false
        val tailWidth = resources.getDimensionPixelSize(R.dimen.module_floating_review_tail_width)
        val tailHeight = resources.getDimensionPixelSize(R.dimen.module_floating_review_tail_height)
        val panelOffset = resources.getDimensionPixelSize(
            R.dimen.module_floating_review_tail_panel_offset
        )
        var measurementChanged = false

        (panel.layoutParams as? FrameLayout.LayoutParams)?.let { panelParams ->
            val targetTop = if (layout.placement == FloatingSpeechPlacement.BELOW_PET) panelOffset else 0
            val targetBottom = if (layout.placement == FloatingSpeechPlacement.ABOVE_PET) panelOffset else 0
            if (panelParams.topMargin != targetTop || panelParams.bottomMargin != targetBottom) {
                panelParams.topMargin = targetTop
                panelParams.bottomMargin = targetBottom
                panel.layoutParams = panelParams
                invalidateCardMeasurement()
                measurementChanged = true
            }
        }

        (tail.layoutParams as? FrameLayout.LayoutParams)?.let { tailParams ->
            val targetLeftMargin = layout.tailCenterX - (tailWidth * 0.82f).roundToInt()
            val targetGravity = Gravity.START or when (layout.placement) {
                FloatingSpeechPlacement.ABOVE_PET -> Gravity.BOTTOM
                FloatingSpeechPlacement.BELOW_PET -> Gravity.TOP
            }
            if (tailParams.width != tailWidth ||
                tailParams.height != tailHeight ||
                tailParams.leftMargin != targetLeftMargin ||
                tailParams.gravity != targetGravity
            ) {
                tailParams.width = tailWidth
                tailParams.height = tailHeight
                tailParams.leftMargin = targetLeftMargin
                tailParams.gravity = targetGravity
                tail.layoutParams = tailParams
            }
        }
        tail.placement = layout.placement
        return measurementChanged
    }

    private fun measureCardForPosition(card: View, maxWidth: Int): Pair<Int, Int> {
        if (cachedCardWidth <= 0 || cachedCardHeight <= 0) {
            card.measure(
                View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            cachedCardWidth = card.measuredWidth
            cachedCardHeight = card.measuredHeight
        }
        return cachedCardWidth to cachedCardHeight
    }

    private fun invalidateCardMeasurement() {
        cachedCardWidth = 0
        cachedCardHeight = 0
    }

    private fun isCardVisible(): Boolean = cardView?.visibility == View.VISIBLE

    private fun applyCardOpacity() {
        cardView?.alpha = resolveCardAlpha(currentSettings.cardOpacityPercent)
    }

    private fun applyBallOpacity() {
        ballView?.alpha = resolveBallAlpha(currentSettings.ballOpacityPercent)
    }

    private fun applyFloatingAppearance() {
        applyBallSize()
        applyBallOpacity()
        applyCardOpacity()
    }

    private fun applyBallSize() {
        val params = ballParams ?: return
        val (petWidth, petHeight) = getPetWindowSize()
        if (params.width == petWidth && params.height == petHeight) return
        params.width = petWidth
        params.height = petHeight
        ballView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        if (isCardVisible()) updateFloatingSpeechLayout()
    }

    private fun persistBallPosition(
        position: FloatingBallPosition,
        dockState: FloatingDockState? = null
    ) {
        updateLocalBallState(position, dockState)
        serviceScope.launch {
            floatingWordController.updateBallPosition(position.x, position.y, dockState)
        }
    }

    private fun updateLocalBallState(
        position: FloatingBallPosition,
        dockState: FloatingDockState? = null
    ) {
        updateCurrentSettings(
            currentSettings.copy(
                floatingBallX = position.x,
                floatingBallY = position.y,
                dockState = dockState
            )
        )
    }

    private fun needsPersistence(position: FloatingBallPosition): Boolean {
        return position.x != currentSettings.floatingBallX ||
            position.y != currentSettings.floatingBallY ||
            currentSettings.dockState != null
    }

    private fun getMovementBounds(settings: FloatingWordSettings): FloatingMovementBounds {
        val safeArea = getSafeDisplayRect()
        val (petWidth, petHeight) = getPetWindowSize()
        return dockManager.createBounds(
            safeArea = FloatingAvailableArea(
                left = safeArea.left,
                top = safeArea.top,
                right = safeArea.right,
                bottom = safeArea.bottom
            ),
            ballWidthPx = petWidth,
            ballHeightPx = petHeight,
            config = settings.dockConfig
        )
    }

    private fun getPetWindowSize(): Pair<Int, Int> {
        val scale = resolveBallSizeScale(currentSettings.ballSizePercent)
        val width = resources.getDimensionPixelSize(R.dimen.feature_floating_review_pet_width)
        val height = resources.getDimensionPixelSize(R.dimen.feature_floating_review_pet_height)
        return Pair((width * scale).roundToInt(), (height * scale).roundToInt())
    }

    private fun getSafeDisplayRect(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.displayCutout()
            )
            return Rect(
                bounds.left + insets.left,
                bounds.top + insets.top,
                bounds.right - insets.right,
                bounds.bottom - insets.bottom
            )
        }

        val display = windowManager.defaultDisplay
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val rootInsets = ballView?.rootWindowInsets
        val leftInset = 0
        val rightInset = 0
        val topInset = rootInsets?.systemWindowInsetTop
            ?.takeIf { it > 0 }
            ?: getSystemDimension("status_bar_height")
        val bottomInset = listOf(
            rootInsets?.stableInsetBottom ?: 0,
            rootInsets?.systemWindowInsetBottom ?: 0,
            getSystemDimension("navigation_bar_height")
        ).maxOrNull() ?: 0
        return Rect(
            leftInset,
            topInset,
            metrics.widthPixels - rightInset,
            metrics.heightPixels - bottomInset
        )
    }

    private fun getSystemDimension(name: String): Int {
        val resourceId = resources.getIdentifier(name, "dimen", "android")
        if (resourceId == 0) return 0
        return resources.getDimensionPixelSize(resourceId)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.module_floating_review_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun updateNotification(content: String) {
        when (
            resolveFloatingNotificationUpdateAction(
                lastDeliveredContent = lastDeliveredNotificationContent,
                pendingContent = pendingNotificationContent,
                incomingContent = content
            )
        ) {
            FloatingNotificationUpdateAction.KEEP -> return
            FloatingNotificationUpdateAction.CANCEL_PENDING -> {
                notificationUpdateGeneration++
                notificationUpdateJob?.cancel()
                notificationUpdateJob = null
                pendingNotificationContent = null
                return
            }
            FloatingNotificationUpdateAction.REPLACE_PENDING -> Unit
        }
        notificationUpdateJob?.cancel()
        pendingNotificationContent = content
        val generation = ++notificationUpdateGeneration
        notificationUpdateJob = serviceScope.launch {
            try {
                delay(FLOATING_NOTIFICATION_FIRST_FRAME_DELAY_MS)
                val notification = withContext(Dispatchers.Default) {
                    buildNotification(content)
                }
                withContext(Dispatchers.IO) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, notification)
                }
                if (generation == notificationUpdateGeneration) {
                    lastDeliveredNotificationContent = content
                }
            } finally {
                if (generation == notificationUpdateGeneration) {
                    pendingNotificationContent = null
                    notificationUpdateJob = null
                }
            }
        }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.module_floating_review_ic_volume_up)
            .setContentTitle(getString(R.string.module_floating_review_notification_title))
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
