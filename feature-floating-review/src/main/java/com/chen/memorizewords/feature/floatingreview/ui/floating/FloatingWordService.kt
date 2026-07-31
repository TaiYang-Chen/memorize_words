package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.coroutines.resume

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

internal fun canReuseCurrentFloatingWord(
    hasCurrentWord: Boolean,
    wordSequenceRefreshPending: Boolean,
    loadedSequenceMatches: Boolean
): Boolean {
    return hasCurrentWord && !wordSequenceRefreshPending && loadedSequenceMatches
}

internal fun shouldReloadFloatingCharacterPack(
    revisionMatches: Boolean,
    packReady: Boolean
): Boolean = !revisionMatches || !packReady

internal enum class FloatingCardSettingsAction {
    NONE,
    LOAD_NEXT,
    RENDER_CURRENT
}

internal fun resolveFloatingCardSettingsAction(
    cardVisible: Boolean,
    hasCurrentWord: Boolean,
    wordSequenceChanged: Boolean,
    fieldConfigsChanged: Boolean
): FloatingCardSettingsAction {
    if (!cardVisible) return FloatingCardSettingsAction.NONE
    return when {
        wordSequenceChanged && !hasCurrentWord -> FloatingCardSettingsAction.LOAD_NEXT
        fieldConfigsChanged && hasCurrentWord -> FloatingCardSettingsAction.RENDER_CURRENT
        fieldConfigsChanged -> FloatingCardSettingsAction.LOAD_NEXT
        else -> FloatingCardSettingsAction.NONE
    }
}

internal enum class FloatingServiceRunMode {
    NOT_STARTED,
    ENABLED
}

internal data class FloatingSettingsChange(
    val enabledChanged: Boolean = false,
    val autoStartChanged: Boolean = false,
    val characterPackChanged: Boolean = false,
    val ballSizeChanged: Boolean = false,
    val ballOpacityChanged: Boolean = false,
    val ballPositionChanged: Boolean = false,
    val cardOpacityChanged: Boolean = false,
    val cardGapChanged: Boolean = false,
    val fieldConfigsChanged: Boolean = false,
    val wordSequenceChanged: Boolean = false
)

internal fun resolveFloatingSettingsChange(
    previous: FloatingWordSettings,
    updated: FloatingWordSettings
): FloatingSettingsChange {
    return FloatingSettingsChange(
        enabledChanged = previous.enabled != updated.enabled,
        autoStartChanged =
            previous.autoStartOnBoot != updated.autoStartOnBoot ||
                previous.autoStartOnAppLaunch != updated.autoStartOnAppLaunch,
        characterPackChanged = previous.selectedCharacterPackId != updated.selectedCharacterPackId,
        ballSizeChanged = previous.ballSizePercent != updated.ballSizePercent,
        ballOpacityChanged = previous.ballOpacityPercent != updated.ballOpacityPercent,
        ballPositionChanged =
            previous.floatingBallX != updated.floatingBallX ||
                previous.floatingBallY != updated.floatingBallY ||
                previous.dockConfig != updated.dockConfig ||
                previous.dockState != updated.dockState,
        cardOpacityChanged = previous.cardOpacityPercent != updated.cardOpacityPercent,
        cardGapChanged = previous.cardGapDp != updated.cardGapDp,
        fieldConfigsChanged = previous.fieldConfigs != updated.fieldConfigs,
        wordSequenceChanged =
            previous.sourceType != updated.sourceType ||
                previous.orderType != updated.orderType ||
                previous.selectedWordIds != updated.selectedWordIds
    )
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
    return runMode == FloatingServiceRunMode.ENABLED &&
        snapshot.settingsEnabled &&
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

internal fun shouldStopColdNonStartingRequest(
    ballViewAttached: Boolean,
    lifecycleOperationInProgress: Boolean
): Boolean = !ballViewAttached && !lifecycleOperationInProgress

internal fun isFloatingNonStartingAction(action: String?): Boolean =
    action == FloatingWordActions.ACTION_APPLY_CHARACTER_PACK

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
        const val ACTION_APPLY_CHARACTER_PACK = FloatingWordActions.ACTION_APPLY_CHARACTER_PACK

        private const val CHANNEL_ID = "floating_word_review_channel"
        private const val NOTIFICATION_ID = 5321
        private const val EMPTY_PLACEHOLDER = "-"
        private const val RUNTIME_HEALTH_CHECK_INTERVAL_MS = 60_000L
        private const val CARD_HEIGHT_TRANSITION_DURATION_MS = 240L
    }

    private sealed interface CardRenderState {
        data class Status(val messageRes: Int) : CardRenderState

        data class WordContent(
            val word: Word,
            val definitions: List<WordDefinitions>,
            val examples: List<WordExample>,
            val settings: FloatingWordSettings
        ) : CardRenderState
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
    private var cardLoadJob: Job? = null
    private var cardHeightAnimator: ValueAnimator? = null
    private var cardMeasureView: View? = null
    private var renderedCardState: CardRenderState? = null
    private var lockedCardPlacement: FloatingSpeechPlacement? = null
    private var cardLoadInProgress = false
    private var notificationUpdateJob: Job? = null
    private var notificationUpdateGeneration = 0L
    private var pendingNotificationContent: String? = null
    private var lastDeliveredNotificationContent: String? = null
    private var cardRequestGeneration = 0L
    private var cardRequestedVisible = false
    private var wordSequenceRefreshPending = true
    private var cardPresentationRefreshPending = false
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
    private val characterPackReloadMutex = Mutex()

    private var isDragging = false
    private var lastBallTapEventTimeMillis: Long? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var dragStartBallX = 0
    private var dragStartBallY = 0
    private var ballGestureDetector: GestureDetector? = null
    private var lastMovementBounds: FloatingMovementBounds? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        ensureChannel()
        settingsJob = serviceScope.launch {
            floatingWordController.observeSettings().collect { settings ->
                val observedGeneration = operationGeneration
                val change = updateCurrentSettings(settings)
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
                    runMode == FloatingServiceRunMode.ENABLED &&
                    isServiceOperationActive(observedGeneration)
                ) {
                    applyRunningSettingsChange(
                        change = change,
                        settings = settings,
                        operationGeneration = observedGeneration
                    )
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
        val isNonStartingAction = isFloatingNonStartingAction(action)
        if (stopping) {
            if (isNonStartingAction) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            stopping = false
        }
        if (
            isNonStartingAction &&
            shouldStopColdNonStartingRequest(
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
        if (!isNonStartingAction) {
            lifecycleOperationJob?.cancel()
            runtimeHealthJob?.cancel()
            runtimeHealthJob = null
            operationGeneration++
        }
        val requestGeneration = operationGeneration
        if (!isNonStartingAction) {
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
                if (!isNonStartingAction) {
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
                    ACTION_APPLY_CHARACTER_PACK ->
                        if (ensureForegroundAndViews(requestGeneration)) {
                            applyCharacterPack(
                                requestGeneration = requestGeneration,
                                requestedPackId = requestedCharacterPackId,
                                downloadRequestId = downloadRequestId
                            )
                        }
                    else -> ensureForegroundAndViews(
                        operationGeneration = requestGeneration,
                        activationRequestId = activationRequestId
                    )
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
        if (!isNonStartingAction) lifecycleOperationJob = operationJob
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        cancelCardHeightAnimation()
        lockedCardPlacement = null
        reconcileBallPosition(persistIfNeeded = true)
        relayoutRenderedCard(animate = false)
    }

    override fun onDestroy() {
        stopping = true
        operationGeneration++
        removeViews()
        floatingPetController.release()
        settingsJob?.cancel()
        runtimeHealthJob?.cancel()
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
        if (!settings.enabled) {
            stopFloating(requestGeneration = operationGeneration)
            return false
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
            reloadInstalledCharacterPackIfNeeded(selectedPackId, installed)
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
        runMode = FloatingServiceRunMode.ENABLED
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
    ): SpritePackId? = characterPackReloadMutex.withLock {
        val installedRevision = installed ?: try {
            characterPackRepository.getInstalled(packId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return@withLock null
        val revision = installedRevision.toCharacterPackRevision()
        val targetPackId = SpritePackId(packId)
        if (!shouldReloadFloatingCharacterPack(
                revisionMatches = revision == loadedCharacterPackRevision,
                packReady = floatingPetController.isPackReady(targetPackId)
            )
        ) {
            return@withLock targetPackId
        }
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
        val settings = floatingWordController.getSettings()
        if (!isServiceOperationActive(operationGeneration)) return true
        val change = updateCurrentSettings(settings)
        if (!settings.enabled) return false
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
            runMode == FloatingServiceRunMode.ENABLED &&
            ballView?.isAttachedToWindow == true &&
            cardView?.isAttachedToWindow == true
        ) {
            if (change.characterPackChanged) {
                loadedCharacterPackRevision = null
            }
            if (change.characterPackChanged) {
                reloadInstalledCharacterPackIfNeeded(selectedPackId)
            }
        }
        applyAttachedSurfaceSettingsChange(change)
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
        val loadedPackId = reloadInstalledCharacterPackIfNeeded(selectedPackId, installed)
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
            cardMeasureView = null
            renderedCardState = null
            lockedCardPlacement = null
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
        cardRequestedVisible = false
        cancelPendingCardLoad()
        wordSequenceRefreshPending = true
        notificationUpdateGeneration++
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
        pendingNotificationContent = null

        val oldBallView = ballView
        val oldCardView = cardView
        ballView = null
        cardView = null
        ballParams = null
        cardParams = null
        lastMovementBounds = null
        cancelCardHeightAnimation()
        cardMeasureView = null
        renderedCardState = null
        lockedCardPlacement = null
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
        val safeArea = getSafeDisplayRect().toFloatingSpeechSafeArea()
        val edgeMargin = resources.getDimensionPixelSize(
            R.dimen.module_floating_review_card_edge_margin
        )
        val cardWidth = resolveFloatingCardWidth(
            safeArea = safeArea,
            preferredWidthPx = resources.getDimensionPixelSize(
                R.dimen.module_floating_review_card_width
            ),
            edgeMarginPx = edgeMargin
        )
        return WindowManager.LayoutParams(
            cardWidth,
            resources.getDimensionPixelSize(R.dimen.module_floating_review_card_min_height),
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
                    if (isCardVisible()) cancelCardHeightAnimation()
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
        lockedCardPlacement = null
        applyBallPosition(result.position)
        relayoutRenderedCard(animate = false)
        persistBallPosition(result.position, result.dockState)
        floatingPetController.playEvent(PetEvent.DRAG_ENDED)
    }

    private fun handleBallSingleTap() {
        when (
            resolveSingleTapAction(
                cardRequestedVisible,
                hasCurrentWordForCurrentSequence()
            )
        ) {
            FloatingBallSingleTapAction.ShowCard -> showCard()

            FloatingBallSingleTapAction.ShowNextCard -> {
                showNextWord()
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
        cardView?.findViewById<View>(R.id.module_floating_review_btn_power)?.setOnClickListener {
            serviceScope.launch {
                floatingActivationCoordinator.disableFloating()
            }
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
        lockedCardPlacement = null
        cardView?.visibility = View.GONE
        if (wasVisible || wasRequestedVisible) floatingPetController.setCardVisible(false)
    }

    private fun hasCurrentWordForCurrentSequence(): Boolean {
        return canReuseCurrentFloatingWord(
            hasCurrentWord = currentWord != null,
            wordSequenceRefreshPending = wordSequenceRefreshPending,
            loadedSequenceMatches = loadedWordSequenceKey == currentSettings.wordSequenceKey()
        )
    }

    private fun refreshCurrentCardPresentation() {
        if (!cardPresentationRefreshPending || !cardRequestedVisible || !isCardVisible()) return
        val word = currentWord ?: return
        beginCardLoad(
            onFailure = { }
        ) { generation ->
            val settings = resolveLatestSettings()
            val content = withContext(Dispatchers.IO) {
                floatingWordController.loadCardContent(word, settings)
            }
            if (
                !isCurrentCardRequest(generation) ||
                currentWord?.id != word.id ||
                currentSettings.fieldConfigs != settings.fieldConfigs
            ) return@beginCardLoad
            commitWordCard(
                word = word,
                definitions = content.definitions,
                examples = content.examples,
                settings = currentSettings,
                animate = true
            )
        }
    }

    private fun showNextWord() {
        val hadRenderedWord = renderedCardState is CardRenderState.WordContent
        if (!hadRenderedWord) renderLoadingCard()
        if (!cardRequestedVisible) showCard()
        beginCardLoad(
            onFailure = {
                if (!hadRenderedWord) {
                    commitStatusCard(
                        messageRes = R.string.module_floating_review_empty,
                        animate = isCardVisible()
                    )
                }
            }
        ) { generation ->
            val settings = resolveLatestSettings()
            val sequenceKey = settings.wordSequenceKey()
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
            if (wordChanged) {
                floatingPetController.playEvent(PetEvent.WORD_CHANGED)
            }
            val renderSettings = currentSettings
            if (word == null || content == null) {
                commitStatusCard(
                    messageRes = R.string.module_floating_review_empty,
                    animate = isCardVisible()
                )
            } else {
                commitWordCard(
                    word = word,
                    definitions = content.definitions,
                    examples = content.examples,
                    settings = renderSettings,
                    animate = isCardVisible()
                )
                updateNotification(word.word)
                serviceScope.launch(Dispatchers.IO) {
                    runCatching { floatingWordController.recordDisplay(word.id) }
                        .onFailure { if (it is CancellationException) throw it }
                }
            }
        }
    }

    private fun showCard() {
        val wasVisible = isCardVisible()
        cardRequestedVisible = true
        val card = cardView
        if (!wasVisible) relayoutRenderedCard(animate = false)
        card?.visibility = View.VISIBLE
        if (!wasVisible) floatingPetController.setCardVisible(true)
        card?.post {
            if (cardRequestedVisible && cardView === card) {
                reconcileBallPosition(persistIfNeeded = false)
                relayoutRenderedCard(animate = false)
            }
        }
        if (hasCurrentWordForCurrentSequence() && cardPresentationRefreshPending) {
            refreshCurrentCardPresentation()
        }
    }

    private fun beginCardLoad(
        onFailure: suspend (Throwable) -> Unit,
        block: suspend (generation: Long) -> Unit
    ) {
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
        cancelCardHeightAnimation()
        setCardLoadInProgress(false)
    }

    private fun isCurrentCardRequest(generation: Long): Boolean {
        return generation == cardRequestGeneration && cardRequestedVisible && cardView != null
    }

    private fun setCardLoadInProgress(loading: Boolean) {
        cardLoadInProgress = loading
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

    private fun updateCurrentSettings(settings: FloatingWordSettings): FloatingSettingsChange {
        if (currentSettings == settings) return FloatingSettingsChange()
        val change = resolveFloatingSettingsChange(currentSettings, settings)
        currentSettings = settings
        settingsRevision++
        if (change.wordSequenceChanged) {
            wordSequenceRefreshPending = true
            loadedWordSequenceKey = null
        }
        if (change.fieldConfigsChanged) cardPresentationRefreshPending = true
        return change
    }

    private suspend fun applyRunningSettingsChange(
        change: FloatingSettingsChange,
        settings: FloatingWordSettings,
        operationGeneration: Long
    ) {
        if (change.characterPackChanged) {
            loadedCharacterPackRevision = null
            val selectedPackId = settings.selectedCharacterPackId?.takeIf { it.isNotBlank() }
            if (
                selectedPackId != null &&
                isServiceOperationActive(operationGeneration) &&
                ballView?.isAttachedToWindow == true &&
                cardView?.isAttachedToWindow == true
            ) {
                reloadInstalledCharacterPackIfNeeded(selectedPackId)
            }
        }
        applyAttachedSurfaceSettingsChange(change)
    }

    private fun applyAttachedSurfaceSettingsChange(change: FloatingSettingsChange) {
        if (ballView?.isAttachedToWindow == true && !isDragging) {
            if (change.ballSizeChanged) applyBallSize()
            if (change.ballOpacityChanged) applyBallOpacity()
            if (change.ballSizeChanged || change.ballPositionChanged) {
                reconcileBallPosition(persistIfNeeded = false)
            }
        }
        val cardVisible = isCardVisible()
        if (cardVisible) {
            if (change.cardOpacityChanged) applyCardOpacity()
            if (change.cardGapChanged) relayoutRenderedCard(animate = true)
        }
        when (
            resolveFloatingCardSettingsAction(
                cardVisible = cardVisible,
                hasCurrentWord = currentWord != null,
                wordSequenceChanged = change.wordSequenceChanged,
                fieldConfigsChanged = change.fieldConfigsChanged
            )
        ) {
            FloatingCardSettingsAction.LOAD_NEXT -> showNextWord()
            FloatingCardSettingsAction.RENDER_CURRENT -> refreshCurrentCardPresentation()
            FloatingCardSettingsAction.NONE -> Unit
        }
    }

    private fun renderLoadingCard() {
        commitCardStateImmediately(
            CardRenderState.Status(R.string.feature_floating_review_loading)
        )
    }

    private suspend fun commitStatusCard(messageRes: Int, animate: Boolean) {
        commitCardState(CardRenderState.Status(messageRes), animate)
    }

    private suspend fun commitWordCard(
        word: Word,
        definitions: List<WordDefinitions>,
        examples: List<WordExample>,
        settings: FloatingWordSettings,
        animate: Boolean
    ) {
        commitCardState(
            state = CardRenderState.WordContent(word, definitions, examples, settings),
            animate = animate
        )
    }

    private suspend fun commitCardState(state: CardRenderState, animate: Boolean) {
        val width = resolveCurrentCardWidth()
        val naturalHeight = measureCardState(state, width)
        applyCardStateToVisibleView(state)
        renderedCardState = state
        transitionCardToNaturalHeight(naturalHeight, animate)
    }

    private fun commitCardStateImmediately(state: CardRenderState) {
        val width = resolveCurrentCardWidth()
        val naturalHeight = measureCardState(state, width)
        applyCardStateToVisibleView(state)
        renderedCardState = state
        applyNaturalCardHeightImmediately(naturalHeight)
    }

    private fun applyCardStateToVisibleView(state: CardRenderState) {
        val target = cardView ?: return
        val hasWordContent = renderCardStateInto(target, state, loadImages = true)
        currentDefinitions = when {
            state is CardRenderState.WordContent && hasWordContent -> state.definitions
            else -> emptyList()
        }
        if (state is CardRenderState.WordContent) {
            cardPresentationRefreshPending = false
            if (hasWordContent) refreshFavoriteState(state.word)
        }
        target.findViewById<ScrollView>(R.id.module_floating_review_content_scroll)
            ?.scrollTo(0, 0)
        setCardLoadInProgress(cardLoadInProgress)
    }

    private fun renderCardStateInto(
        target: View,
        state: CardRenderState,
        loadImages: Boolean
    ): Boolean {
        return when (state) {
            is CardRenderState.Status -> {
                renderStatusCardInto(target, state.messageRes)
                false
            }

            is CardRenderState.WordContent -> renderCardInto(
                target = target,
                word = state.word,
                definitions = state.definitions,
                examples = state.examples,
                settings = state.settings,
                loadImages = loadImages
            )
        }
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

    private fun renderCardInto(
        target: View,
        word: Word,
        definitions: List<WordDefinitions>,
        examples: List<WordExample>,
        settings: FloatingWordSettings,
        loadImages: Boolean
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
        renderExtraFields(container, word, definitions, examples, configs, loadImages)
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
        configs: List<FloatingWordFieldConfig>,
        loadImages: Boolean
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

                    FloatingWordFieldType.IMAGE -> buildImageView(
                        url = word.mnemonicImageUrl,
                        sizeDp = config.fontSizeSp,
                        loadImage = loadImages
                    )
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

    private fun buildImageView(url: String?, sizeDp: Int, loadImage: Boolean): View {
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
            if (loadImage) load(url)
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

    private fun measureCardState(state: CardRenderState, cardWidth: Int): Int {
        val measurementView = cardMeasureView ?: LayoutInflater.from(this)
            .inflate(R.layout.module_floating_review_view_floating_card, null)
            .also { view ->
                val panel = view.findViewById<View>(R.id.module_floating_review_card_panel)
                (panel.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    panel.layoutParams = params
                }
                val scroll = view.findViewById<ScrollView>(
                    R.id.module_floating_review_content_scroll
                )
                (scroll.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.weight = 0f
                    scroll.layoutParams = params
                }
                cardMeasureView = view
            }
        renderCardStateInto(measurementView, state, loadImages = false)
        measurementView.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return measurementView.measuredHeight.coerceAtLeast(1)
    }

    private fun resolveCurrentCardWidth(): Int {
        val safeArea = getSafeDisplayRect().toFloatingSpeechSafeArea()
        val edgeMargin = resources.getDimensionPixelSize(
            R.dimen.module_floating_review_card_edge_margin
        )
        return resolveFloatingCardWidth(
            safeArea = safeArea,
            preferredWidthPx = resources.getDimensionPixelSize(
                R.dimen.module_floating_review_card_width
            ),
            edgeMarginPx = edgeMargin
        )
    }

    private fun relayoutRenderedCard(animate: Boolean) {
        val state = renderedCardState ?: return
        val width = resolveCurrentCardWidth()
        val naturalHeight = measureCardState(state, width)
        if (animate) {
            serviceScope.launch {
                transitionCardToNaturalHeight(naturalHeight, animate = true)
            }
        } else {
            applyNaturalCardHeightImmediately(naturalHeight)
        }
    }

    private fun updateFloatingSpeechLayout() {
        val params = cardParams ?: return
        val width = resolveCurrentCardWidth()
        if (params.width != width) lockedCardPlacement = null
        val placement = resolveOrLockCardPlacement()
        applyCardWindowGeometry(
            cardWidth = width,
            cardHeight = params.height.coerceAtLeast(1),
            placement = placement
        )
    }

    private fun applyNaturalCardHeightImmediately(naturalHeight: Int) {
        cancelCardHeightAnimation()
        val width = resolveCurrentCardWidth()
        if (cardParams?.width != width) lockedCardPlacement = null
        val placement = resolveOrLockCardPlacement()
        val targetHeight = resolveTargetCardHeight(naturalHeight, width, placement)
        applyCardWindowGeometry(width, targetHeight, placement)
    }

    private suspend fun transitionCardToNaturalHeight(
        naturalHeight: Int,
        animate: Boolean
    ) {
        val params = cardParams ?: return
        val width = resolveCurrentCardWidth()
        if (params.width != width) lockedCardPlacement = null
        val placement = resolveOrLockCardPlacement()
        val targetHeight = resolveTargetCardHeight(naturalHeight, width, placement)
        val startHeight = params.height.coerceAtLeast(1)
        val animationsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ValueAnimator.areAnimatorsEnabled()
        if (
            !animate ||
            !animationsEnabled ||
            !isCardVisible() ||
            startHeight == targetHeight
        ) {
            cancelCardHeightAnimation()
            applyCardWindowGeometry(width, targetHeight, placement)
            return
        }

        cancelCardHeightAnimation()
        suspendCancellableCoroutine { continuation ->
            var cancelled = false
            val animator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
                duration = CARD_HEIGHT_TRANSITION_DURATION_MS
                interpolator = AnimationUtils.loadInterpolator(
                    this@FloatingWordService,
                    android.R.interpolator.fast_out_slow_in
                )
                addUpdateListener { valueAnimator ->
                    if (
                        !cardRequestedVisible ||
                        cardView?.isAttachedToWindow != true
                    ) {
                        cancel()
                        return@addUpdateListener
                    }
                    applyCardWindowGeometry(
                        cardWidth = width,
                        cardHeight = valueAnimator.animatedValue as Int,
                        placement = placement
                    )
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (cardHeightAnimator === animation) cardHeightAnimator = null
                        if (!cancelled && cardRequestedVisible) {
                            applyCardWindowGeometry(width, targetHeight, placement)
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                })
            }
            cardHeightAnimator = animator
            continuation.invokeOnCancellation {
                if (animator.isStarted) animator.cancel()
            }
            animator.start()
        }
    }

    private fun cancelCardHeightAnimation() {
        val animator = cardHeightAnimator ?: return
        cardHeightAnimator = null
        animator.cancel()
    }

    private fun resolveOrLockCardPlacement(): FloatingSpeechPlacement {
        lockedCardPlacement?.let { return it }
        val placement = speechLayoutEngine.resolvePlacementForMinimumHeight(
            safeArea = getSafeDisplayRect().toFloatingSpeechSafeArea(),
            petBounds = currentPetBounds(),
            minimumCardHeightPx = resources.getDimensionPixelSize(
                R.dimen.module_floating_review_card_min_height
            ),
            config = currentSpeechLayoutConfig()
        )
        lockedCardPlacement = placement
        return placement
    }

    private fun resolveTargetCardHeight(
        naturalHeight: Int,
        cardWidth: Int,
        placement: FloatingSpeechPlacement
    ): Int {
        val availableHeight = speechLayoutEngine.availableHeight(
            safeArea = getSafeDisplayRect().toFloatingSpeechSafeArea(),
            petBounds = currentPetBounds(),
            config = currentSpeechLayoutConfig(),
            placement = placement
        )
        return resolveFloatingCardTargetHeight(
            naturalHeightPx = naturalHeight,
            minimumHeightPx = resources.getDimensionPixelSize(
                R.dimen.module_floating_review_card_min_height
            ),
            cardWidthPx = cardWidth,
            placementAvailableHeightPx = availableHeight
        )
    }

    private fun applyCardWindowGeometry(
        cardWidth: Int,
        cardHeight: Int,
        placement: FloatingSpeechPlacement
    ) {
        val params = cardParams ?: return
        val card = cardView ?: return
        val layout = speechLayoutEngine.resolveAnchored(
            safeArea = getSafeDisplayRect().toFloatingSpeechSafeArea(),
            petBounds = currentPetBounds(),
            cardSize = FloatingSpeechCardSize(cardWidth, cardHeight),
            config = currentSpeechLayoutConfig(),
            placement = placement
        )
        applyFloatingSpeechTailLayout(layout)
        val changed = params.width != cardWidth ||
            params.height != cardHeight ||
            params.x != layout.cardX ||
            params.y != layout.cardY
        params.width = cardWidth
        params.height = cardHeight
        params.x = layout.cardX
        params.y = layout.cardY
        if (changed && card.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(card, params) }
        }
    }

    private fun currentPetBounds(): FloatingSpeechPetBounds {
        val ball = ballParams
        val (petWidth, petHeight) = getPetWindowSize()
        return FloatingSpeechPetBounds(
            x = ball?.x ?: 0,
            y = ball?.y ?: 0,
            width = petWidth,
            height = petHeight
        )
    }

    private fun currentSpeechLayoutConfig(): FloatingSpeechLayoutConfig {
        return FloatingSpeechLayoutConfig(
            edgeMarginPx = resources.getDimensionPixelSize(
                R.dimen.module_floating_review_card_edge_margin
            ),
            clearancePx = currentSettings.cardGapDp.dpToPx(this),
            tailWidthPx = resources.getDimensionPixelSize(
                R.dimen.module_floating_review_tail_width
            ),
            tailSafeInsetPx = resources.getDimensionPixelSize(
                R.dimen.module_floating_review_tail_safe_inset
            ),
            tailSlotHeightPx = resources.getDimensionPixelSize(
                R.dimen.module_floating_review_tail_panel_offset
            )
        )
    }

    private fun Rect.toFloatingSpeechSafeArea(): FloatingSpeechSafeArea {
        return FloatingSpeechSafeArea(left, top, right, bottom)
    }

    private fun applyFloatingSpeechTailLayout(layout: FloatingSpeechLayout) {
        val card = cardView ?: return
        val panel = card.findViewById<View>(R.id.module_floating_review_card_panel) ?: return
        val tail = card.findViewById<FloatingSpeechTailView>(
            R.id.module_floating_review_card_tail
        ) ?: return
        val tailWidth = resources.getDimensionPixelSize(R.dimen.module_floating_review_tail_width)
        val tailHeight = resources.getDimensionPixelSize(R.dimen.module_floating_review_tail_height)
        val panelOffset = resources.getDimensionPixelSize(
            R.dimen.module_floating_review_tail_panel_offset
        )
        (panel.layoutParams as? FrameLayout.LayoutParams)?.let { panelParams ->
            val targetTop = if (layout.placement == FloatingSpeechPlacement.BELOW_PET) panelOffset else 0
            val targetBottom = if (layout.placement == FloatingSpeechPlacement.ABOVE_PET) panelOffset else 0
            if (panelParams.topMargin != targetTop || panelParams.bottomMargin != targetBottom) {
                panelParams.topMargin = targetTop
                panelParams.bottomMargin = targetBottom
                panel.layoutParams = panelParams
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
