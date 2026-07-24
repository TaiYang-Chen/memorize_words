package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

import android.os.Looper
import com.chen.memorizewords.core.sprite.FloatingPetAnimationSessionFactory
import com.chen.memorizewords.core.sprite.FloatingPetRenderHost
import com.chen.memorizewords.core.sprite.SpriteClipId
import com.chen.memorizewords.core.sprite.SpriteClipSpec
import com.chen.memorizewords.core.sprite.SpritePack
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.sprite.SpritePackRepository
import com.chen.memorizewords.core.sprite.SpritePackRuntimeRole
import com.chen.memorizewords.core.sprite.SpritePlaybackMode
import com.chen.memorizewords.core.sprite.SpriteReverseResult
import com.chen.memorizewords.core.sprite.SpritePlaybackSession
import java.util.ArrayDeque
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingPetController @Inject constructor(
    private val repository: SpritePackRepository,
    private val sessionFactory: FloatingPetAnimationSessionFactory,
    private val actionPolicy: FloatingPetActionPolicy,
    private val packContractValidator: FloatingPetPackContractValidator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<FloatingPetCommand>(Channel.UNLIMITED)
    private val playbackStateMachine = FloatingPetPlaybackStateMachine()
    private var view: FloatingPetRenderHost? = null
    private var session: SpritePlaybackSession? = null
    private var sessionView: FloatingPetRenderHost? = null
    private var loadJob: Job? = null
    private var detachedReleaseJob: Job? = null
    private var openPreloadJob: Job? = null
    private var loopPreloadJob: Job? = null
    private var closePreloadJob: Job? = null
    private var memoryTrimJob: Job? = null
    private var requestedVisible = false
    private var submittedVisible = false
    private var idlePrewarmEnabled = true
    private var packLoadGeneration = 0L
    private var attachmentGeneration = 0L
    private var optionalResumeVisible: Boolean? = null
    private val pendingEvents = ArrayDeque<PetEvent>()
    private var submittedPackId: SpritePackId? = null
    private var activePackId: SpritePackId? = null
    private var playbackRecoveryAttempted = false
    private var trimPending = false
    private var released = false
    private val pendingPackLoadCompletions = mutableSetOf<CompletableDeferred<SpritePackId?>>()

    var state: FloatingPetPlaybackState
        get() = playbackStateMachine.state
        private set(value) = playbackStateMachine.transitionTo(value)

    init {
        scope.launch {
            for (command in commands) {
                try {
                    handle(command)
                } catch (cancelled: CancellationException) {
                    completeCommandPackLoad(command, null)
                    throw cancelled
                } catch (_: Exception) {
                    completeCommandPackLoad(command, null)
                    if (
                        command !is FloatingPetCommand.Detach &&
                        command != FloatingPetCommand.Release
                    ) {
                        recoverAfterCommandFailure()
                    }
                }
            }
        }
    }

    fun attach(view: FloatingPetRenderHost, packId: SpritePackId) {
        checkMainThread()
        if (released) return
        attachmentGeneration++
        this.view = view
        submittedPackId = null
        switchPack(packId)
    }

    fun setCardVisible(visible: Boolean) {
        checkMainThread()
        if (released) return
        if (visible) idlePrewarmEnabled = true
        if (visible == submittedVisible) return
        if (commands.trySend(FloatingPetCommand.SetCardVisible(visible)).isSuccess) {
            submittedVisible = visible
        }
    }

    fun playOptionalAction(actionId: String) {
        checkMainThread()
        if (!released) commands.trySend(FloatingPetCommand.PlayOptionalAction(actionId))
    }

    fun playEvent(event: PetEvent) {
        checkMainThread()
        if (!released) commands.trySend(FloatingPetCommand.PlayEvent(event))
    }

    fun switchPack(packId: SpritePackId) {
        checkMainThread()
        if (released || packId == submittedPackId) return
        if (commands.trySend(FloatingPetCommand.SwitchPack(packId)).isSuccess) {
            submittedPackId = packId
        }
    }

    fun forceReloadPack(packId: SpritePackId) {
        checkMainThread()
        if (released) return
        if (commands.trySend(FloatingPetCommand.SwitchPack(packId, forceReload = true)).isSuccess) {
            submittedPackId = packId
        }
    }

    suspend fun forceReloadPackAndAwait(packId: SpritePackId): SpritePackId? {
        checkMainThread()
        if (released) return null
        val completion = CompletableDeferred<SpritePackId?>()
        pendingPackLoadCompletions += completion
        if (
            commands.trySend(
                FloatingPetCommand.SwitchPack(
                    packId = packId,
                    forceReload = true,
                    completion = completion
                )
            ).isFailure
        ) {
            completePackLoad(completion, null)
            return null
        }
        submittedPackId = packId
        return completion.await()
    }

    fun isPackReady(packId: SpritePackId): Boolean {
        checkMainThread()
        return !released &&
            session != null &&
            activePackId == packId &&
            state != FloatingPetPlaybackState.UNINITIALIZED &&
            state != FloatingPetPlaybackState.SWITCHING_PACK &&
            state != FloatingPetPlaybackState.RELEASED
    }

    fun detach() {
        checkMainThread()
        if (released) return
        requestedVisible = false
        submittedVisible = false
        optionalResumeVisible = null
        idlePrewarmEnabled = true
        submittedPackId = null
        val generation = ++attachmentGeneration
        commands.trySend(FloatingPetCommand.Detach(generation))
    }

    fun trimMemory() {
        checkMainThread()
        idlePrewarmEnabled = false
        trimPending = true
        val activeSession = session ?: run {
            trimPending = false
            return
        }
        when (state) {
            FloatingPetPlaybackState.IDLE -> applyMemoryTrim(activeSession)
            FloatingPetPlaybackState.VISIBLE_LOOP -> scheduleVisibleMemoryTrim(activeSession)
            else -> Unit
        }
    }

    fun release() {
        checkMainThread()
        if (!released) commands.trySend(FloatingPetCommand.Release)
    }

    private suspend fun handle(command: FloatingPetCommand) {
        when (command) {
            is FloatingPetCommand.SetCardVisible -> {
                playbackRecoveryAttempted = false
                pendingEvents.clear()
                requestedVisible = command.visible
                renderRequestedVisibility()
            }
            is FloatingPetCommand.PlayEvent -> handlePetEvent(command.event)
            is FloatingPetCommand.PlayOptionalAction -> playOptional(command.actionId)
            is FloatingPetCommand.SwitchPack -> loadPack(
                packId = command.packId,
                forceReload = command.forceReload,
                completion = command.completion
            )
            is FloatingPetCommand.Detach -> {
                if (command.attachmentGeneration == attachmentGeneration) detachNow()
            }
            FloatingPetCommand.Release -> releaseNow()
        }
    }

    private suspend fun loadPack(
        packId: SpritePackId,
        forceReload: Boolean = false,
        completion: CompletableDeferred<SpritePackId?>? = null
    ) {
        loadJob?.cancelAndJoin()
        detachedReleaseJob?.join()
        detachedReleaseJob = null
        val targetView = view ?: run {
            completion?.let { completePackLoad(it, null) }
            return
        }
        val requestGeneration = ++packLoadGeneration
        optionalResumeVisible = null
        pendingEvents.clear()
        loadJob = scope.launch {
            var pendingSession: SpritePlaybackSession? = null
            var installedSession: SpritePlaybackSession? = null
            var loadedPackId: SpritePackId? = null
            try {
                val resolved = resolvePack(packId)
                var preparedPack: SpritePack? = null
                var preparedSession: SpritePlaybackSession? = null
                var lastFailure: Exception? = null
                for (candidate in runtimeCandidates(resolved)) {
                    try {
                        packContractValidator.validate(candidate.manifest)
                        val currentSession = session
                        if (shouldReuseFloatingPetSession(
                                forceReload = forceReload,
                                hasCurrentSession = currentSession != null,
                                sameView = sessionView === targetView,
                                samePackId = currentSession?.manifest?.packId ==
                                    candidate.manifest.packId,
                                samePackVersion = currentSession?.manifest?.packVersion ==
                                    candidate.manifest.packVersion
                            )
                        ) {
                            submittedPackId = candidate.manifest.packId
                            loadedPackId = runtimePackLoadCompletionId(candidate)
                            return@launch
                        }
                        trimFloatingPetSessionBeforeReplacement(currentSession)
                        val candidateSession = sessionFactory.create(candidate, targetView, scope)
                        pendingSession = candidateSession
                        val idle = actionPolicy.resolveStandardAction(
                            candidate.manifest,
                            StandardPetAction.IDLE
                        )
                        // The staged renderer is transparent, so this validates a real first draw
                        // before it can replace the active renderer.
                        candidateSession.prepare(idle, presentFrame = true)
                        preparedPack = candidate
                        preparedSession = candidateSession
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        pendingSession?.let { failedSession ->
                            withContext(NonCancellable) {
                                try {
                                    closeAndAwaitRelease(failedSession)
                                } catch (closeFailure: Exception) {
                                    error.addSuppressed(closeFailure)
                                }
                            }
                        }
                        pendingSession = null
                        lastFailure = error
                    }
                }
                val pack = preparedPack ?: throw (
                    lastFailure ?: IllegalStateException(
                        "Character pack has no runtime-compatible revision"
                    )
                )
                val newSession = checkNotNull(preparedSession)
                if (
                    requestGeneration != packLoadGeneration ||
                    view !== targetView ||
                    released
                ) {
                    return@launch
                }
                openPreloadJob?.cancel()
                openPreloadJob = null
                loopPreloadJob?.cancel()
                loopPreloadJob = null
                closePreloadJob?.cancel()
                closePreloadJob = null
                memoryTrimJob?.cancel()
                memoryTrimJob = null
                val oldSession = session
                newSession.activate()
                state = FloatingPetPlaybackState.SWITCHING_PACK
                session = newSession
                sessionView = targetView
                installedSession = newSession
                activePackId = pack.manifest.packId
                submittedPackId = pack.manifest.packId
                playbackRecoveryAttempted = false
                pendingSession = null
                if (oldSession != null) {
                    withContext(NonCancellable) {
                        closeAndAwaitRelease(oldSession)
                    }
                }
                currentCoroutineContext().ensureActive()
                prewarmBoundEventTextures(newSession)
                state = FloatingPetPlaybackState.IDLE
                if (!requestedVisible) scheduleIdlePrewarm(newSession)
                renderRequestedVisibility()
                loadedPackId = runtimePackLoadCompletionId(pack)
            } catch (cancelled: CancellationException) {
                if (
                    session === installedSession &&
                    state == FloatingPetPlaybackState.SWITCHING_PACK
                ) {
                    state = FloatingPetPlaybackState.IDLE
                }
                throw cancelled
            } catch (_: Exception) {
                if (requestGeneration == packLoadGeneration) {
                    if (submittedPackId == packId) submittedPackId = activePackId
                    if (session != null && sessionView !== targetView) {
                        val staleSession = session
                        try {
                            if (staleSession != null) {
                                withContext(NonCancellable) {
                                    closeAndAwaitRelease(staleSession)
                                }
                            }
                        } finally {
                            session = null
                            sessionView = null
                            activePackId = null
                        }
                    }
                    if (session == null) {
                        state = FloatingPetPlaybackState.UNINITIALIZED
                    } else {
                        if (state == FloatingPetPlaybackState.SWITCHING_PACK) {
                            state = FloatingPetPlaybackState.IDLE
                        }
                        if (state == FloatingPetPlaybackState.IDLE) {
                            if (requestedVisible) {
                                renderRequestedVisibility()
                            } else {
                                scheduleIdlePrewarm(checkNotNull(session))
                            }
                        }
                    }
                }
            } finally {
                pendingSession?.let { unusedSession ->
                    withContext(NonCancellable) {
                        closeAndAwaitRelease(unusedSession)
                    }
                }
                completion?.let { completePackLoad(it, loadedPackId) }
                if (requestGeneration == packLoadGeneration) loadJob = null
            }
        }
    }

    private fun completeCommandPackLoad(
        command: FloatingPetCommand,
        loadedPackId: SpritePackId?
    ) {
        (command as? FloatingPetCommand.SwitchPack)?.completion?.let { completion ->
            completePackLoad(completion, loadedPackId)
        }
    }

    private fun completePackLoad(
        completion: CompletableDeferred<SpritePackId?>,
        loadedPackId: SpritePackId?
    ) {
        pendingPackLoadCompletions.remove(completion)
        completion.complete(loadedPackId)
    }

    private suspend fun resolvePack(packId: SpritePackId): SpritePack {
        return repository.find(packId)
            ?: throw IllegalStateException("Character pack ${packId.value} is unavailable")
    }

    private fun runtimeCandidates(pack: SpritePack): List<SpritePack> =
        listOfNotNull(pack, pack.runtimeFallback)

    private fun renderRequestedVisibility() {
        val activeSession = session ?: return
        if (state == FloatingPetPlaybackState.OPTIONAL) {
            val resumeVisible = optionalResumeVisible ?: requestedVisible
            optionalResumeVisible = null
            state = if (resumeVisible) {
                FloatingPetPlaybackState.VISIBLE_LOOP
            } else {
                FloatingPetPlaybackState.IDLE
            }
        }
        when (playbackStateMachine.visibilityDirective(requestedVisible)) {
            FloatingPetVisibilityDirective.NONE -> return
            FloatingPetVisibilityDirective.REVERSE_TO_VISIBLE -> {
                when (activeSession.reversePlaybackDirection(
                        onComplete = {
                            if (session !== activeSession || !requestedVisible) {
                                return@reversePlaybackDirection
                            }
                            finishOpenTransition(activeSession)
                        },
                        onError = { handlePlaybackError(activeSession) }
                    )) {
                    SpriteReverseResult.REVERSED -> {
                        state = FloatingPetPlaybackState.OPENING
                    }
                    SpriteReverseResult.NOT_STARTED -> {
                        activeSession.cancelPlayback()
                        finishOpenTransition(activeSession)
                    }
                    SpriteReverseResult.UNSUPPORTED -> Unit
                }
                return
            }
            FloatingPetVisibilityDirective.OPEN -> playOpen(activeSession)
            FloatingPetVisibilityDirective.REVERSE_TO_IDLE -> {
                state = FloatingPetPlaybackState.CLOSING
                loopPreloadJob?.cancel()
                loopPreloadJob = null
                when (activeSession.reversePlaybackDirection(
                        onComplete = { finishCloseTransition(activeSession) },
                        onError = { handlePlaybackError(activeSession) }
                    )) {
                    SpriteReverseResult.REVERSED -> return
                    SpriteReverseResult.NOT_STARTED -> {
                        activeSession.cancelPlayback()
                        finishCloseTransition(activeSession)
                        return
                    }
                    SpriteReverseResult.UNSUPPORTED -> {
                        state = FloatingPetPlaybackState.OPENING
                        return
                    }
                }
                playClose(activeSession)
            }
            FloatingPetVisibilityDirective.CLOSE -> playClose(activeSession)
        }
    }

    private fun playOpen(activeSession: SpritePlaybackSession) {
        state = FloatingPetPlaybackState.OPENING
        openPreloadJob?.cancel()
        openPreloadJob = null
        loopPreloadJob?.cancel()
        loopPreloadJob = null
        closePreloadJob?.cancel()
        closePreloadJob = null
        memoryTrimJob?.cancel()
        memoryTrimJob = null
        playStandard(activeSession, StandardPetAction.CARD_OPEN) {
            finishOpenTransition(activeSession)
        }
        val openClip = activeSession.manifest.clip(
            actionPolicy.resolveStandardAction(
                activeSession.manifest,
                StandardPetAction.CARD_OPEN
            )
        )
        scheduleVisibleLoopPreload(
            activeSession,
            resolveLoopPreloadDelayMillis(openClip)
        )
    }

    private fun finishOpenTransition(activeSession: SpritePlaybackSession) {
        if (session !== activeSession) return
        if (!requestedVisible) {
            playClose(activeSession)
            return
        }
        state = FloatingPetPlaybackState.VISIBLE_LOOP
        queueEventIfBound(activeSession, PetEvent.CARD_OPENED)
        if (!playNextPendingEvent(activeSession)) {
            playVisibleLoop(activeSession)
        }
    }

    private fun playVisibleLoop(activeSession: SpritePlaybackSession) {
        state = FloatingPetPlaybackState.VISIBLE_LOOP
        playStandard(activeSession, StandardPetAction.CARD_VISIBLE)
        if (trimPending) {
            scheduleVisibleMemoryTrim(activeSession)
        } else {
            scheduleClosePrewarm(activeSession)
        }
    }

    private fun playClose(activeSession: SpritePlaybackSession) {
        loopPreloadJob?.cancel()
        loopPreloadJob = null
        closePreloadJob?.cancel()
        closePreloadJob = null
        memoryTrimJob?.cancel()
        memoryTrimJob = null
        state = FloatingPetPlaybackState.CLOSING
        playStandard(activeSession, StandardPetAction.CARD_CLOSE) {
            finishCloseTransition(activeSession)
        }
    }

    private fun finishCloseTransition(activeSession: SpritePlaybackSession) {
        if (session !== activeSession) return
        state = FloatingPetPlaybackState.IDLE
        if (requestedVisible) {
            renderRequestedVisibility()
        } else {
            queueEventIfBound(activeSession, PetEvent.CARD_CLOSED)
            if (!playNextPendingEvent(activeSession)) {
                playIdleAndContinue(activeSession)
            }
        }
    }

    private fun playIdleAndContinue(activeSession: SpritePlaybackSession) {
        state = FloatingPetPlaybackState.IDLE
        playStandard(activeSession, StandardPetAction.IDLE) {
            if (session !== activeSession) return@playStandard
            state = FloatingPetPlaybackState.IDLE
            if (requestedVisible) {
                renderRequestedVisibility()
            } else {
                scheduleIdlePrewarm(activeSession)
            }
        }
    }

    private fun scheduleIdlePrewarm(activeSession: SpritePlaybackSession) {
        openPreloadJob?.cancel()
        loopPreloadJob?.cancel()
        loopPreloadJob = null
        closePreloadJob?.cancel()
        closePreloadJob = null
        if (!idlePrewarmEnabled) {
            openPreloadJob = null
            applyMemoryTrim(activeSession)
            return
        }
        openPreloadJob = scope.launch {
            prewarmOpen(activeSession)
            prewarmBoundEventTextures(activeSession)
            if (
                session === activeSession &&
                state == FloatingPetPlaybackState.IDLE &&
                !requestedVisible &&
                idlePrewarmEnabled
            ) {
                loopPreloadJob?.cancel()
                loopPreloadJob = scope.launch {
                    prewarmVisibleLoop(activeSession)
                }
            }
        }
    }

    private fun scheduleVisibleMemoryTrim(activeSession: SpritePlaybackSession) {
        memoryTrimJob?.cancel()
        closePreloadJob?.cancel()
        closePreloadJob = null
        memoryTrimJob = scope.launch {
            prewarmVisibleLoop(activeSession)
            if (
                session === activeSession &&
                state == FloatingPetPlaybackState.VISIBLE_LOOP &&
                trimPending
            ) {
                applyMemoryTrim(activeSession)
            }
        }
    }

    private fun applyMemoryTrim(activeSession: SpritePlaybackSession) {
        if (session !== activeSession) return
        openPreloadJob?.cancel()
        openPreloadJob = null
        loopPreloadJob?.cancel()
        loopPreloadJob = null
        closePreloadJob?.cancel()
        closePreloadJob = null
        activeSession.trimMemory()
        trimPending = false
    }

    private fun scheduleVisibleLoopPreload(
        activeSession: SpritePlaybackSession,
        delayMillis: Long
    ) {
        if (loopPreloadJob?.isActive == true) return
        loopPreloadJob = scope.launch {
            if (delayMillis > 0L) delay(delayMillis)
            if (session === activeSession && requestedVisible) {
                prewarmVisibleLoop(activeSession)
            }
        }
    }

    private fun scheduleClosePrewarm(activeSession: SpritePlaybackSession) {
        closePreloadJob?.cancel()
        closePreloadJob = scope.launch {
            if (
                session === activeSession &&
                state == FloatingPetPlaybackState.VISIBLE_LOOP &&
                requestedVisible
            ) {
                prewarmClose(activeSession)
            }
        }
    }

    private suspend fun prewarmOpen(activeSession: SpritePlaybackSession) {
        val openClip = actionPolicy.resolveStandardAction(
            activeSession.manifest,
            StandardPetAction.CARD_OPEN
        )
        try {
            activeSession.preloadClipHead(openClip, frameCount = OPEN_PRELOAD_FRAME_COUNT)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preloading is best effort; playback can still decode on demand.
        }
    }

    private suspend fun prewarmVisibleLoop(activeSession: SpritePlaybackSession) {
        val visibleLoop = actionPolicy.resolveStandardAction(
            activeSession.manifest,
            StandardPetAction.CARD_VISIBLE
        )
        try {
            activeSession.preloadLoop(visibleLoop)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preloading is best effort; playback can still decode on demand.
        }
    }

    private suspend fun prewarmClose(activeSession: SpritePlaybackSession) {
        val closeClip = actionPolicy.resolveStandardAction(
            activeSession.manifest,
            StandardPetAction.CARD_CLOSE
        )
        try {
            activeSession.preloadClipHead(closeClip, frameCount = CLOSE_PRELOAD_FRAME_COUNT)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Playback can still decode the transition on demand.
        }
    }

    private suspend fun prewarmBoundEventTextures(activeSession: SpritePlaybackSession) {
        for (event in EVENT_TEXTURE_PRELOAD_ORDER) {
            if (session !== activeSession) return
            val clipId = resolveFiniteEventClip(activeSession, event) ?: continue
            try {
                // KTX2 uploads the complete optional texture on the GL thread here. play() never
                // falls back to I/O/transcoding, so an early tap can only play or fail safely.
                activeSession.preloadClipHead(clipId, frameCount = 1)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Optional actions are best effort and may be absent or exceed the current LRU room.
            }
        }
    }

    private fun handlePetEvent(event: PetEvent) {
        val activeSession = session ?: return
        val clipId = resolveFiniteEventClip(activeSession, event) ?: return
        when (state) {
            FloatingPetPlaybackState.IDLE,
            FloatingPetPlaybackState.VISIBLE_LOOP -> playOptionalClip(activeSession, clipId)
            FloatingPetPlaybackState.OPENING,
            FloatingPetPlaybackState.CLOSING,
            FloatingPetPlaybackState.OPTIONAL -> queueEvent(event)
            else -> Unit
        }
    }

    private fun playOptional(actionId: String) {
        if (
            state != FloatingPetPlaybackState.IDLE &&
            state != FloatingPetPlaybackState.VISIBLE_LOOP &&
            state != FloatingPetPlaybackState.OPTIONAL
        ) return
        val activeSession = session ?: return
        val clipId = actionPolicy.resolveOptionalAction(activeSession.manifest, actionId) ?: return
        playOptionalClip(activeSession, clipId)
    }

    private fun playOptionalClip(
        activeSession: SpritePlaybackSession,
        clipId: SpriteClipId
    ): Boolean {
        if (session !== activeSession) return false
        if (
            state != FloatingPetPlaybackState.IDLE &&
            state != FloatingPetPlaybackState.VISIBLE_LOOP &&
            state != FloatingPetPlaybackState.OPTIONAL
        ) return false
        val clip = activeSession.manifest.clip(clipId)
        if (clip.playbackMode == SpritePlaybackMode.LOOP) return false
        playbackRecoveryAttempted = false
        loopPreloadJob?.cancel()
        loopPreloadJob = null
        closePreloadJob?.cancel()
        closePreloadJob = null
        memoryTrimJob?.cancel()
        memoryTrimJob = null
        optionalResumeVisible = requestedVisible
        state = FloatingPetPlaybackState.OPTIONAL
        activeSession.play(
            clipId = clipId,
            onComplete = { restoreAfterOptional(activeSession) },
            onError = { handlePlaybackError(activeSession) }
        )
        return true
    }

    private fun restoreAfterOptional(activeSession: SpritePlaybackSession) {
        if (session !== activeSession) return
        if (playNextPendingEvent(activeSession)) return
        val resumeVisible = optionalResumeVisible ?: requestedVisible
        optionalResumeVisible = null
        if (requestedVisible != resumeVisible) {
            state = if (resumeVisible) {
                FloatingPetPlaybackState.VISIBLE_LOOP
            } else {
                FloatingPetPlaybackState.IDLE
            }
            renderRequestedVisibility()
        } else if (requestedVisible) {
            playVisibleLoop(activeSession)
        } else {
            playIdleAndContinue(activeSession)
        }
    }

    private fun queueEventIfBound(
        activeSession: SpritePlaybackSession,
        event: PetEvent
    ) {
        if (resolveFiniteEventClip(activeSession, event) != null) {
            queueEvent(event)
        }
    }

    private fun queueEvent(event: PetEvent) {
        if (pendingEvents.peekLast() == event) return
        if (pendingEvents.size >= MAX_PENDING_EVENTS) {
            pendingEvents.removeFirst()
        }
        pendingEvents.addLast(event)
    }

    private fun playNextPendingEvent(activeSession: SpritePlaybackSession): Boolean {
        while (pendingEvents.isNotEmpty()) {
            val event = pendingEvents.removeFirst()
            val clipId = resolveFiniteEventClip(activeSession, event) ?: continue
            if (playOptionalClip(activeSession, clipId)) return true
        }
        return false
    }

    private fun resolveFiniteEventClip(
        activeSession: SpritePlaybackSession,
        event: PetEvent
    ): SpriteClipId? {
        val clipId = actionPolicy.resolveEventAction(activeSession.manifest, event) ?: return null
        return clipId.takeIf {
            activeSession.manifest.clip(it).playbackMode != SpritePlaybackMode.LOOP
        }
    }

    private fun playStandard(
        activeSession: SpritePlaybackSession,
        action: StandardPetAction,
        onComplete: (() -> Unit)? = null
    ) {
        val clipId: SpriteClipId = actionPolicy.resolveStandardAction(activeSession.manifest, action)
        val clip = activeSession.manifest.clip(clipId)
        activeSession.play(
            clipId = clipId,
            onComplete = if (clip.playbackMode == SpritePlaybackMode.LOOP) null else onComplete,
            onError = { handlePlaybackError(activeSession) }
        )
    }

    private fun handlePlaybackError(activeSession: SpritePlaybackSession) {
        if (session !== activeSession || released) return
        optionalResumeVisible = null
        pendingEvents.clear()
        openPreloadJob?.cancel()
        openPreloadJob = null
        loopPreloadJob?.cancel()
        loopPreloadJob = null
        closePreloadJob?.cancel()
        closePreloadJob = null
        memoryTrimJob?.cancel()
        memoryTrimJob = null
        activeSession.trimMemory()
        when (
            playbackStateMachine.recoveryDirective(
                requestedVisible = requestedVisible,
                retryAlreadyAttempted = playbackRecoveryAttempted
            )
        ) {
            FloatingPetRecoveryDirective.RETRY_VISIBLE_LOOP -> {
                playbackRecoveryAttempted = true
                playVisibleLoop(activeSession)
            }
            FloatingPetRecoveryDirective.RETRY_IDLE -> {
                playbackRecoveryAttempted = true
                playIdleRecovery(activeSession)
            }
            FloatingPetRecoveryDirective.HOLD_VISIBLE,
            FloatingPetRecoveryDirective.HOLD_IDLE -> settlePlaybackState()
        }
    }

    private fun playIdleRecovery(activeSession: SpritePlaybackSession) {
        state = FloatingPetPlaybackState.IDLE
        val idleClipId = actionPolicy.resolveStandardAction(
            activeSession.manifest,
            StandardPetAction.IDLE
        )
        activeSession.play(
            clipId = idleClipId,
            onComplete = {
                if (session !== activeSession) return@play
                if (requestedVisible) {
                    renderRequestedVisibility()
                } else {
                    scheduleIdlePrewarm(activeSession)
                }
            },
            onError = { handlePlaybackError(activeSession) }
        )
    }

    private fun recoverAfterCommandFailure() {
        if (released) return
        val activeSession = session
        if (activeSession == null) {
            state = FloatingPetPlaybackState.UNINITIALIZED
            return
        }
        try {
            handlePlaybackError(activeSession)
        } catch (_: Exception) {
            settlePlaybackState()
        }
    }

    private fun settlePlaybackState() {
        state = if (requestedVisible) {
            FloatingPetPlaybackState.VISIBLE_LOOP
        } else {
            FloatingPetPlaybackState.IDLE
        }
    }

    private fun detachNow() {
        packLoadGeneration++
        optionalResumeVisible = null
        pendingEvents.clear()
        loadJob?.cancel()
        loadJob = null
        openPreloadJob?.cancel()
        openPreloadJob = null
        loopPreloadJob?.cancel()
        loopPreloadJob = null
        closePreloadJob?.cancel()
        closePreloadJob = null
        memoryTrimJob?.cancel()
        memoryTrimJob = null
        val detachedSession = session
        var releaseJob: Job? = null
        try {
            if (detachedSession != null) {
                detachedSession.close()
                releaseJob = scope.launch {
                    detachedSession.awaitReleased()
                }
            }
        } finally {
            if (releaseJob != null) detachedReleaseJob = releaseJob
            session = null
            sessionView = null
            activePackId = null
            submittedPackId = null
            view = null
            requestedVisible = false
            submittedVisible = false
            idlePrewarmEnabled = true
            playbackRecoveryAttempted = false
            trimPending = false
            state = FloatingPetPlaybackState.UNINITIALIZED
        }
    }

    private fun releaseNow() {
        try {
            detachNow()
        } finally {
            released = true
            state = FloatingPetPlaybackState.RELEASED
            commands.close()
            pendingPackLoadCompletions.toList().forEach { completion ->
                completePackLoad(completion, null)
            }
            scope.coroutineContext[Job]?.cancel()
        }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Floating pet commands must run on the main thread"
        }
    }

    private suspend fun closeAndAwaitRelease(retiredSession: SpritePlaybackSession) {
        val closeCompleted = try {
            retiredSession.close()
            true
        } catch (_: Exception) {
            false
        }
        if (closeCompleted) retiredSession.awaitReleased()
    }

    companion object {
        private const val OPEN_PRELOAD_FRAME_COUNT = 12
        private const val CLOSE_PRELOAD_FRAME_COUNT = 3
        private const val MAX_PENDING_EVENTS = 8
        // Low priority first: the bounded LRU leaves the most interaction-critical textures hot.
        val EVENT_TEXTURE_PRELOAD_ORDER = listOf(
            PetEvent.CARD_OPENED,
            PetEvent.CARD_CLOSED,
            PetEvent.DRAG_ENDED,
            PetEvent.DRAG_STARTED,
            PetEvent.WORD_CHANGED,
            PetEvent.FAVORITE_REMOVED,
            PetEvent.FAVORITE_ADDED,
            PetEvent.PET_TAP
        )

        private fun resolveLoopPreloadDelayMillis(transitionClip: SpriteClipSpec): Long {
            val bufferedFrames = minOf(OPEN_PRELOAD_FRAME_COUNT, transitionClip.frameCount)
            return ((transitionClip.frameDurationNanos * bufferedFrames) / 1_000_000L)
                .coerceAtLeast(1L)
        }
    }
}

internal suspend fun trimFloatingPetSessionBeforeReplacement(
    session: SpritePlaybackSession?
) {
    session?.trimMemoryAndAwait()
}

internal fun shouldReuseFloatingPetSession(
    forceReload: Boolean,
    hasCurrentSession: Boolean,
    sameView: Boolean,
    samePackId: Boolean,
    samePackVersion: Boolean
): Boolean = !forceReload &&
    hasCurrentSession &&
    sameView &&
    samePackId &&
    samePackVersion
internal fun runtimePackLoadCompletionId(pack: SpritePack): SpritePackId? =
    pack.manifest.packId.takeIf {
        pack.runtimeRole == SpritePackRuntimeRole.PRIMARY
    }
