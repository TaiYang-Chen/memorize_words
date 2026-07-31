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
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.chen.memorizewords.core.sprite.FloatingPetRenderHost
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.FloatingWordEntryExtras
import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.model.membership.MembershipFeatureAccess
import com.chen.memorizewords.domain.account.usecase.membership.ResolveMembershipFeatureAccessUseCase
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingDevicePreferences
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeError
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeEvent
import com.chen.memorizewords.domain.floating.model.FloatingRuntimePhase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import com.chen.memorizewords.domain.floating.repository.FloatingDevicePreferencesRepository
import com.chen.memorizewords.domain.floating.repository.FloatingRuntimeSessionRepository
import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import com.chen.memorizewords.domain.floating.service.FloatingRuntimeReporter
import com.chen.memorizewords.domain.floating.service.FloatingRuntimeController
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class FloatingCardActionState(
    val refreshEnabled: Boolean,
    val favoriteEnabled: Boolean,
    val copyEnabled: Boolean
)

internal fun resolveCardActionState(hasWord: Boolean): FloatingCardActionState {
    return FloatingCardActionState(
        refreshEnabled = true,
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

private data class FloatingRuntimeCommand(
    val sessionId: String,
    val revision: Long,
    val configVersion: Long
)

internal data class FloatingSettingsChange(
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
        characterPackChanged = previous.selectedCharacterPackId != updated.selectedCharacterPackId,
        ballSizeChanged = previous.ballSizePercent != updated.ballSizePercent,
        ballOpacityChanged = previous.ballOpacityPercent != updated.ballOpacityPercent,
        ballPositionChanged = false,
        cardOpacityChanged = previous.cardOpacityPercent != updated.cardOpacityPercent,
        cardGapChanged = previous.cardGapDp != updated.cardGapDp,
        fieldConfigsChanged = previous.fieldConfigs != updated.fieldConfigs,
        wordSequenceChanged =
            previous.sourceType != updated.sourceType ||
                previous.orderType != updated.orderType ||
                previous.selectedWordIds != updated.selectedWordIds
    )
}

private data class CharacterPackInstallRevision(
    val packId: String,
    val packVersion: Int,
    val installedDirectory: String
)

internal fun resolveBallPositionForSettings(
    preferences: FloatingDevicePreferences,
    bounds: FloatingMovementBounds,
    previousBounds: FloatingMovementBounds?,
    dockManager: FloatingDockManager = FloatingDockManager()
): FloatingBallPosition {
    preferences.dockState?.let { dockState ->
        dockManager.resolveDocked(
            bounds = bounds,
            config = preferences.dockConfig,
            dockState = dockState
        )?.let { docked ->
            return docked.position
        }
    }
    if (previousBounds != null) {
        dockManager.resolveAnchoredFreePosition(
            previousBounds = previousBounds,
            newBounds = bounds,
            x = preferences.floatingBallX,
            y = preferences.floatingBallY
        )?.let { anchoredPosition ->
            return anchoredPosition
        }
    }
    if (preferences.floatingBallX != 0 || preferences.floatingBallY != 0) {
        return dockManager.clampToFree(
            bounds,
            preferences.floatingBallX,
            preferences.floatingBallY
        )
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
        const val ACTION_RECONFIGURE = FloatingWordActions.ACTION_RECONFIGURE

        private const val CHANNEL_ID = "floating_word_review_channel"
        private const val NOTIFICATION_ID = 5321
        private const val RUNTIME_HEARTBEAT_INTERVAL_MS = 30_000L
        private const val RUNTIME_START_TIMEOUT_MS = 20_000L
        private const val CARD_HEIGHT_TRANSITION_DURATION_MS = 240L
    }

    @Inject
    lateinit var floatingWordController: FloatingWordController

    @Inject
    lateinit var resolveMembershipFeatureAccessUseCase: ResolveMembershipFeatureAccessUseCase

    @Inject
    lateinit var floatingPetController: FloatingPetController

    @Inject
    lateinit var characterPackRepository: CharacterPackRepository

    @Inject
    lateinit var devicePreferencesRepository: FloatingDevicePreferencesRepository

    @Inject
    lateinit var runtimeSessionRepository: FloatingRuntimeSessionRepository

    @Inject
    lateinit var runtimeReporter: FloatingRuntimeReporter

    @Inject
    lateinit var runtimeController: FloatingRuntimeController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dockManager = FloatingDockManager()
    private val cardGeometryEngine = FloatingCardGeometryEngine()
    private lateinit var windowManager: WindowManager
    private lateinit var cardRenderer: FloatingCardRenderer

    private var ballView: View? = null
    private var cardView: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var characterPackRevisionJob: Job? = null
    private var cardCoordinator: FloatingCardCoordinator? = null
    private var settingsJob: Job? = null
    private var runtimeHealthJob: Job? = null
    private var cardLoadJob: Job? = null
    private var renderedCardState: CardRenderState? = null
    private var cardLoadInProgress = false
    private var notificationUpdateJob: Job? = null
    private var notificationUpdateGeneration = 0L
    private var pendingNotificationContent: String? = null
    private var lastDeliveredNotificationContent: String? = null
    private var cardRequestGeneration = 0L
    private var cardRequestedVisible = false
    private var cardPresentationRefreshPending = false

    private var wordSequenceState = FloatingWordSequenceState()
    private var currentWord: Word? = null
    private var currentDefinitions: List<WordDefinitions> = emptyList()
    private var currentSettings: FloatingWordSettings = FloatingWordSettings()
    private var currentDevicePreferences: FloatingDevicePreferences = FloatingDevicePreferences()
    private var settingsRevision = 0L
    private var operationGeneration = 0L
    private var stopping = false
    private var lifecycleOperationJob: Job? = null
    private var loadedCharacterPackRevision: CharacterPackInstallRevision? = null
    private var floatingSurfaceGeneration = 0L
    private val characterPackReloadMutex = Mutex()
    private var activeRuntimeSessionId: String? = null
    private var activeRuntimeRevision: Long? = null
    private var activeRuntimePackId: String? = null

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
        cardRenderer = FloatingCardRenderer(this)
        ensureChannel()
        settingsJob = serviceScope.launch {
            floatingWordController.observeSettings().collect { settings ->
                // Settings update the presentation model only. Runtime commands come from V2.
                updateCurrentSettings(settings)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent?.toRuntimeCommand() ?: return START_NOT_STICKY
        when (intent.action) {
            ACTION_START -> startRuntime(command, startId)
            ACTION_STOP -> serviceScope.launch { stopRuntime(command, startId) }
            ACTION_RECONFIGURE -> serviceScope.launch { reconfigureRuntime(command) }
            else -> return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun startRuntime(command: FloatingRuntimeCommand, startId: Int) {
        lifecycleOperationJob?.cancel()
        runtimeHealthJob?.cancel()
        stopping = false
        operationGeneration++
        val requestGeneration = operationGeneration
        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(getString(R.string.module_floating_review_notification_starting))
            )
        } catch (_: RuntimeException) {
            serviceScope.launch {
                failRuntime(command, FloatingRuntimeError.FOREGROUND_SERVICE_REJECTED)
                stopFloating(requestGeneration = requestGeneration, startId = startId)
            }
            return
        }
        lifecycleOperationJob = serviceScope.launch {
            val session = runtimeSessionFor(command, FloatingRuntimePhase.STARTING) ?: run {
                stopFloating(requestGeneration = requestGeneration, startId = startId)
                return@launch
            }
            activeRuntimeSessionId = session.sessionId
            activeRuntimeRevision = session.revision
            activeRuntimePackId = session.targetPackId
            try {
                if (!canUseFloatingReview()) {
                    failRuntime(command, FloatingRuntimeError.MEMBERSHIP_REQUIRED)
                    stopFloating(requestGeneration = requestGeneration, startId = startId)
                    return@launch
                }
                val started = withTimeoutOrNull(RUNTIME_START_TIMEOUT_MS) {
                    ensureForegroundAndViews(requestGeneration, session)
                } ?: false
                if (!started && isServiceOperationActive(requestGeneration)) {
                    failRuntime(command, FloatingRuntimeError.RENDER_TIMEOUT)
                    stopFloating(requestGeneration = requestGeneration, startId = startId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failRuntime(command, FloatingRuntimeError.RENDER_FAILED)
                stopFloating(requestGeneration = requestGeneration, startId = startId)
            }
        }
    }

    private suspend fun stopRuntime(command: FloatingRuntimeCommand, startId: Int) {
        val current = runtimeSessionRepository.getSnapshot().session
        val ownsActiveSurface = activeRuntimeSessionId == command.sessionId
        if (
            !ownsActiveSurface &&
            (current?.sessionId != command.sessionId || current.revision != command.revision ||
                current.phase != FloatingRuntimePhase.STOPPING)
        ) return
        runtimeReporter.transition(command.sessionId, command.revision, FloatingRuntimeEvent.Stopped)
        stopFloating(startId = startId)
    }

    private suspend fun reconfigureRuntime(command: FloatingRuntimeCommand) {
        val session = runningSessionForReconfigure(command) ?: return
        if (activeRuntimeSessionId != session.sessionId) return
        activeRuntimeRevision = session.revision
        currentDevicePreferences = devicePreferencesRepository.get()
        val settings = resolveLatestSettings()
        applyRunningSettingsChange(
            change = FloatingSettingsChange(
                ballSizeChanged = true,
                ballOpacityChanged = true,
                ballPositionChanged = true,
                cardOpacityChanged = true,
                cardGapChanged = true,
                fieldConfigsChanged = true,
                wordSequenceChanged = true
            ),
            settings = settings,
            operationGeneration = operationGeneration
        )
    }

    private suspend fun runtimeSessionFor(
        command: FloatingRuntimeCommand,
        expectedPhase: FloatingRuntimePhase
    ): FloatingRuntimeSession? {
        val current = runtimeSessionRepository.getSnapshot().session ?: return null
        return current.takeIf {
            it.sessionId == command.sessionId &&
                it.revision == command.revision &&
                it.phase == expectedPhase
        }
    }

    private suspend fun runningSessionForReconfigure(
        command: FloatingRuntimeCommand
    ): FloatingRuntimeSession? {
        val current = runtimeSessionRepository.getSnapshot().session ?: return null
        // Heartbeats advance revision independently. Configuration is monotonic, so applying the
        // latest settings for the same session is safe even when the command revision is stale.
        return current.takeIf {
            it.sessionId == command.sessionId &&
                it.phase == FloatingRuntimePhase.RUNNING &&
                it.configVersion >= command.configVersion
        }
    }

    private suspend fun failRuntime(command: FloatingRuntimeCommand, error: FloatingRuntimeError) {
        runtimeReporter.transition(command.sessionId, command.revision, FloatingRuntimeEvent.Failed(error))
    }

    private fun Intent.toRuntimeCommand(): FloatingRuntimeCommand? {
        val sessionId = getStringExtra(FloatingWordEntryExtras.EXTRA_RUNTIME_SESSION_ID)
            ?.takeIf { it.isNotBlank() } ?: return null
        val revision = getLongExtra(FloatingWordEntryExtras.EXTRA_RUNTIME_REVISION, -1L)
        if (revision < 0L) return null
        return FloatingRuntimeCommand(
            sessionId = sessionId,
            revision = revision,
            configVersion = getLongExtra(FloatingWordEntryExtras.EXTRA_RUNTIME_CONFIG_VERSION, 0L)
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        cardCoordinator?.cancelForConfigurationChange()
        cardRenderer.clearMeasurementView()
        reconcileBallPosition(persistIfNeeded = true, updateCard = false)
        relayoutRenderedCard(animate = false, reselectPlacement = true, remeasure = true)
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
        session: FloatingRuntimeSession
    ): Boolean {
        if (!isServiceOperationActive(operationGeneration)) return false
        resolveLatestSettings()
        currentDevicePreferences = devicePreferencesRepository.get()
        if (!isServiceOperationActive(operationGeneration)) return false
        if (!Settings.canDrawOverlays(this)) {
            failRuntime(
                FloatingRuntimeCommand(session.sessionId, session.revision, session.configVersion),
                FloatingRuntimeError.PERMISSION_DENIED
            )
            return false
        }
        val selectedPackId = session.targetPackId
        if (selectedPackId.isNullOrBlank() || !characterPackRepository.isInstalledUsable(selectedPackId)) {
            failRuntime(
                FloatingRuntimeCommand(session.sessionId, session.revision, session.configVersion),
                FloatingRuntimeError.CHARACTER_UNAVAILABLE
            )
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
            failRuntime(
                FloatingRuntimeCommand(session.sessionId, session.revision, session.configVersion),
                if (failure is SecurityException) {
                    FloatingRuntimeError.PERMISSION_DENIED
                } else {
                    FloatingRuntimeError.RENDER_FAILED
                }
            )
            return false
        }
        val running = runtimeReporter.transition(
            session.sessionId,
            session.revision,
            FloatingRuntimeEvent.RendererReady
        ) ?: return false
        activeRuntimeRevision = running.revision
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
        activeRuntimeSessionId = null
        activeRuntimeRevision = null
        activeRuntimePackId = null
        if (startId == null) stopSelf() else stopSelf(startId)
    }

    private suspend fun canUseFloatingReview(): Boolean {
        return resolveMembershipFeatureAccessUseCase(MembershipFeature.FLOATING_REVIEW) ==
            MembershipFeatureAccess.ALLOWED
    }


    private fun startCharacterPackRevisionMonitoring(operationGeneration: Long) {
        characterPackRevisionJob?.cancel()
        characterPackRevisionJob = serviceScope.launch {
            try {
                characterPackRepository.observeInstalled().collect { installed ->
                    if (!isServiceOperationActive(operationGeneration)) return@collect
                    val selectedPackId = activeRuntimePackId ?: return@collect
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
                delay(RUNTIME_HEARTBEAT_INTERVAL_MS)
                if (!isServiceOperationActive(operationGeneration)) return@launch
                val health = try {
                    refreshRuntimeHealth()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    FloatingRuntimeError.UNKNOWN
                }
                if (!isServiceOperationActive(operationGeneration)) return@launch
                if (health != null) {
                    stopFloating(requestGeneration = operationGeneration)
                    return@launch
                }
            }
        }
    }

    /** Returns a terminal error when the session can no longer remain visible. */
    private suspend fun refreshRuntimeHealth(): FloatingRuntimeError? {
        val activeSessionId = activeRuntimeSessionId ?: return FloatingRuntimeError.UNKNOWN
        val current = runtimeSessionRepository.getSnapshot().session
            ?.takeIf { it.sessionId == activeSessionId } ?: return FloatingRuntimeError.UNKNOWN
        if (current.phase == FloatingRuntimePhase.STOPPING) {
            runtimeReporter.transition(
                current.sessionId,
                current.revision,
                FloatingRuntimeEvent.Stopped
            )
            return FloatingRuntimeError.UNKNOWN
        }
        if (current.phase != FloatingRuntimePhase.RUNNING) return FloatingRuntimeError.UNKNOWN
        val targetPackId = current.targetPackId
        val error = when {
            !Settings.canDrawOverlays(this) -> FloatingRuntimeError.PERMISSION_DENIED
            !canUseFloatingReview() -> FloatingRuntimeError.MEMBERSHIP_REQUIRED
            targetPackId.isNullOrBlank() ||
                !characterPackRepository.isInstalledUsable(targetPackId) ->
                FloatingRuntimeError.CHARACTER_UNAVAILABLE
            else -> null
        }
        if (error != null) {
            runtimeReporter.transition(
                current.sessionId,
                current.revision,
                FloatingRuntimeEvent.Failed(error)
            )
            return error
        }
        val heartbeated = runtimeReporter.transition(
            current.sessionId,
            current.revision,
            FloatingRuntimeEvent.Heartbeat(System.currentTimeMillis())
        ) ?: return FloatingRuntimeError.UNKNOWN
        activeRuntimeRevision = heartbeated.revision
        return null
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
            cardCoordinator?.attached == true
        ) return
        if (ballView != null || cardView != null) removeViews()

        val inflater = LayoutInflater.from(this)
        val newBallView = inflater.inflate(R.layout.module_floating_review_view_floating_ball, null)
        val newCardView = inflater.inflate(R.layout.module_floating_review_view_floating_card, null).apply {
            visibility = View.GONE
        }
        val newBallParams = createBallLayoutParams()
        val newCardParams = createCardLayoutParams()
        val newCardCoordinator = FloatingCardCoordinator(
            root = newCardView,
            windowPort = AndroidFloatingCardWindowPort(
                windowManager = windowManager,
                root = newCardView,
                params = newCardParams
            ),
            geometryEngine = cardGeometryEngine,
            durationMillis = CARD_HEIGHT_TRANSITION_DURATION_MS,
            interpolator = AnimationUtils.loadInterpolator(
                this,
                android.R.interpolator.fast_out_slow_in
            ),
            scope = serviceScope
        )
        var cardAttached = false
        var ballAttached = false

        try {
            newCardCoordinator.attach()
            cardAttached = true
            windowManager.addView(newBallView, newBallParams)
            ballAttached = true

            ballView = newBallView
            cardView = newCardView
            ballParams = newBallParams
            cardCoordinator = newCardCoordinator

            val renderHost = newBallView as? FloatingPetRenderHost
                ?: error("Floating ball layout must use FloatingPetRenderHost as its root")
            floatingPetController.attach(
                renderHost,
                packId,
                loadImmediately = false
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
            if (cardAttached || newCardCoordinator.attached) {
                newCardCoordinator.detach()
            }
            ballView = null
            cardView = null
            ballParams = null
            cardCoordinator = null
            lastMovementBounds = null
            cardRenderer.clearMeasurementView()
            renderedCardState = null
            ballGestureDetector = null
            throw failure
        }
    }

    private fun reconcileBallPosition(
        persistIfNeeded: Boolean,
        updateCard: Boolean = true
    ) {
        val params = ballParams ?: return
        val movementBounds = getMovementBounds(currentDevicePreferences)
        val position = resolveBallPositionForSettings(
            preferences = currentDevicePreferences,
            bounds = movementBounds,
            previousBounds = lastMovementBounds,
            dockManager = dockManager
        )
        val shouldPersist = persistIfNeeded && needsPersistence(position)
        val resolvedDockState = currentDevicePreferences.dockState
            ?.normalized(currentDevicePreferences.dockConfig)
        val positionChanged = params.x != position.x || params.y != position.y
        params.x = position.x
        params.y = position.y
        if (positionChanged) {
            ballView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        }
        updateLocalBallState(position, resolvedDockState)
        lastMovementBounds = movementBounds
        if (updateCard && isCardVisible()) {
            updateFloatingSpeechLayout()
        }
        if (shouldPersist) {
            persistBallPosition(position, resolvedDockState)
        }
    }

    private fun removeViews() {
        floatingSurfaceGeneration++
        cardRequestedVisible = false
        cancelPendingCardLoad()
        wordSequenceState = FloatingWordSequenceState(currentWordId = currentWord?.id)
        notificationUpdateGeneration++
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
        pendingNotificationContent = null

        val oldBallView = ballView
        val oldCardCoordinator = cardCoordinator
        ballView = null
        cardView = null
        ballParams = null
        cardCoordinator = null
        lastMovementBounds = null
        cardRenderer.clearMeasurementView()
        renderedCardState = null
        ballGestureDetector = null
        isDragging = false
        lastBallTapEventTimeMillis = null
        runCatching { floatingPetController.detach() }
        oldBallView?.let(::removeWindowViewSafely)
        oldCardCoordinator?.detach()
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
        if (currentDevicePreferences.dockState != null) {
            currentDevicePreferences = currentDevicePreferences.copy(dockState = null)
        }
    }

    private fun settleDraggedBall() {
        val params = ballParams ?: return
        val result = dockManager.resolveFreeRestingState(
            bounds = getMovementBounds(currentDevicePreferences),
            x = params.x,
            y = params.y
        )
        isDragging = false
        applyBallPosition(result.position, updateCard = false)
        relayoutRenderedCard(animate = true, reselectPlacement = true, remeasure = false)
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

    private fun applyBallPosition(
        position: FloatingBallPosition,
        updateCard: Boolean = true
    ) {
        val params = ballParams ?: return
        params.x = position.x
        params.y = position.y
        ballView?.let { windowManager.updateViewLayout(it, params) }
        if (updateCard && isCardVisible()) updateFloatingSpeechLayout()
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
                runtimeController.requestStop()
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
        cardCoordinator?.hide()
        if (wasVisible || wasRequestedVisible) floatingPetController.setCardVisible(false)
    }

    private fun hasCurrentWordForCurrentSequence(): Boolean {
        return currentWord != null &&
            renderedCardState is CardRenderState.WordContent &&
            wordSequenceState.matches(currentSettings)
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
            val sourceSnapshot = withContext(Dispatchers.IO) {
                floatingWordController.loadWordSource(settings)
            }
            val advance = advanceFloatingWordSequence(wordSequenceState, sourceSnapshot)
            val loadedWord = advance.wordId?.let { wordId ->
                withContext(Dispatchers.IO) {
                    val word = floatingWordController.loadWord(wordId)
                        ?: throw IllegalStateException("Floating word $wordId is unavailable")
                    word to floatingWordController.loadCardContent(word, settings)
                }
            }
            if (!isCurrentCardRequest(generation)) return@beginCardLoad
            if (
                currentSettings.wordSequenceKey() != sequenceKey ||
                currentSettings.fieldConfigs != settings.fieldConfigs
            ) return@beginCardLoad
            val word = loadedWord?.first
            val content = loadedWord?.second
            val wordChanged = word != null && currentWord?.id != word.id
            wordSequenceState = advance.state
            currentWord = word
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
        if (!wasVisible) {
            cardCoordinator?.show(currentCardGeometryInput())
        }
        if (!wasVisible) floatingPetController.setCardVisible(true)
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
        setCardLoadInProgress(false)
    }

    private fun isCurrentCardRequest(generation: Long): Boolean {
        return generation == cardRequestGeneration && cardRequestedVisible && cardView != null
    }

    private fun setCardLoadInProgress(loading: Boolean) {
        cardLoadInProgress = loading
        cardView?.findViewById<View>(R.id.module_floating_review_btn_refresh)?.apply {
            isEnabled = !loading
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
            wordSequenceState = FloatingWordSequenceState(currentWordId = currentWord?.id)
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
            if (change.cardGapChanged) {
                updateFloatingSpeechLayout(recomputeHeightLimit = true)
            }
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
        val naturalHeight = cardRenderer.measure(state, width)
        renderedCardState = state
        cardCoordinator?.commitContent(
            input = currentCardGeometryInput(naturalHeight, width),
            animate = animate
        ) {
            applyCardStateToVisibleView(state)
        }
    }

    private fun commitCardStateImmediately(state: CardRenderState) {
        val width = resolveCurrentCardWidth()
        val naturalHeight = cardRenderer.measure(state, width)
        renderedCardState = state
        cardCoordinator?.commitContentImmediately(
            input = currentCardGeometryInput(naturalHeight, width)
        ) {
            applyCardStateToVisibleView(state)
        }
    }

    private fun applyCardStateToVisibleView(state: CardRenderState) {
        val target = cardView ?: return
        val hasWordContent = cardRenderer.render(target, state, loadImages = true)
        currentDefinitions = when {
            state is CardRenderState.WordContent && hasWordContent -> state.definitions
            else -> emptyList()
        }
        if (state is CardRenderState.WordContent) {
            cardPresentationRefreshPending = false
            if (hasWordContent) refreshFavoriteState(state.word)
        }
        setCardLoadInProgress(cardLoadInProgress)
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
        val text = cardRenderer.buildCopyText(word, currentDefinitions)
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

    private fun relayoutRenderedCard(
        animate: Boolean,
        reselectPlacement: Boolean = false,
        remeasure: Boolean = true
    ) {
        val coordinator = cardCoordinator ?: return
        val width = resolveCurrentCardWidth()
        val naturalHeight = if (remeasure) {
            renderedCardState?.let { cardRenderer.measure(it, width) }
                ?: coordinator.currentNaturalHeight
        } else {
            coordinator.currentNaturalHeight
        }
        serviceScope.launch {
            coordinator.relayout(
                input = currentCardGeometryInput(naturalHeight, width),
                animate = animate,
                reselectPlacement = reselectPlacement
            )
        }
    }

    private fun updateFloatingSpeechLayout(recomputeHeightLimit: Boolean = false) {
        val coordinator = cardCoordinator ?: return
        coordinator.moveAnchor(
            input = currentCardGeometryInput(coordinator.currentNaturalHeight),
            recomputeHeightLimit = recomputeHeightLimit
        )
    }

    private fun currentCardGeometryInput(
        naturalHeight: Int = cardCoordinator?.currentNaturalHeight
            ?: resources.getDimensionPixelSize(R.dimen.module_floating_review_card_min_height),
        cardWidth: Int = resolveCurrentCardWidth()
    ): FloatingCardGeometryInput {
        return FloatingCardGeometryInput(
            safeArea = getSafeDisplayRect().toFloatingSpeechSafeArea(),
            petBounds = currentPetBounds(),
            cardWidth = cardWidth,
            naturalHeight = naturalHeight,
            minimumHeight = resources.getDimensionPixelSize(
                R.dimen.module_floating_review_card_min_height
            ),
            config = currentSpeechLayoutConfig()
        )
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

    private fun isCardVisible(): Boolean = cardCoordinator?.visible == true

    private fun applyCardOpacity() {
        cardCoordinator?.setAlpha(resolveCardAlpha(currentSettings.cardOpacityPercent))
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
        currentDevicePreferences = currentDevicePreferences.copy(
            floatingBallX = position.x,
            floatingBallY = position.y,
            dockState = dockState
        )
    }

    private fun needsPersistence(position: FloatingBallPosition): Boolean {
        return position.x != currentDevicePreferences.floatingBallX ||
            position.y != currentDevicePreferences.floatingBallY ||
            currentDevicePreferences.dockState != null
    }

    private fun getMovementBounds(preferences: FloatingDevicePreferences): FloatingMovementBounds {
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
            config = preferences.dockConfig
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
