package com.chen.memorizewords.core.sprite

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SpritePlaybackState {
    UNINITIALIZED,
    READY,
    PLAYING_ONCE,
    LOOPING,
    RELEASED
}

enum class SpriteReverseResult {
    REVERSED,
    NOT_STARTED,
    UNSUPPORTED
}

interface SpritePlaybackSession : Closeable {
    val manifest: SpritePackManifest
    val state: SpritePlaybackState

    suspend fun prepare(initialClipId: SpriteClipId, presentFrame: Boolean = true)
    fun activate()
    suspend fun preloadClipHead(clipId: SpriteClipId, frameCount: Int = 3)
    suspend fun preloadLoop(clipId: SpriteClipId)
    fun play(
        clipId: SpriteClipId,
        onComplete: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    )
    fun reversePlaybackDirection(
        onComplete: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ): SpriteReverseResult
    fun cancelPlayback()
    fun trimMemory()
    /**
     * Releases non-essential renderer resources and returns only after that release is visible to
     * a replacement renderer. Legacy bitmap sessions trim synchronously on the main thread; GPU
     * sessions override this to provide a barrier on their dedicated GL thread.
     */
    suspend fun trimMemoryAndAwait() {
        trimMemory()
    }
    /** Suspends until every decoded bitmap owned by a closed session has been released. */
    suspend fun awaitReleased()
}

internal class DefaultSpritePlaybackSession(
    override val manifest: SpritePackManifest,
    private val view: SpriteAnimationView,
    private val frameProvider: SpriteFrameProvider,
    private val scheduler: AnimationScheduler,
    private val scope: CoroutineScope,
    private val replacementFrameByteReserve: Long = manifest.atlas.frameWidth.toLong() *
        manifest.atlas.frameHeight * ARGB_8888_BYTES_PER_PIXEL,
    private val startupPrefetchFrames: Int = 1,
    private val queueCapacity: Int = 12,
    poolSize: Int = DEFAULT_POOL_SIZE
) : SpritePlaybackSession {
    private val frameByteCount = manifest.atlas.frameWidth.toLong() *
        manifest.atlas.frameHeight * ARGB_8888_BYTES_PER_PIXEL
    private val maxResidentFrameCount = resolveSessionResidentFrameLimit(
        maxResidentBitmapBytes = MAX_RESIDENT_BITMAP_BYTES,
        frameByteCount = frameByteCount,
        replacementFrameByteReserve = replacementFrameByteReserve
    )
    private val maxLoopFrameCount = manifest.clips.values
        .filter { it.playbackMode == SpritePlaybackMode.LOOP }
        .maxOfOrNull(SpriteClipSpec::frameCount)
        ?: 0
    private val maxTransitionPrefetchFrames = (
        maxResidentFrameCount -
            maxLoopFrameCount -
            CURRENT_FRAME_RESERVE -
            CACHED_TRANSITION_QUEUE_CAPACITY -
            PRODUCER_PENDING_FRAME_RESERVE
        ).coerceAtLeast(0)
    private val maxReusablePoolFrames = (
        maxResidentFrameCount - maxLoopFrameCount - CURRENT_FRAME_RESERVE
        ).coerceAtLeast(1)
    private val bitmapPool = BitmapPool(
        maxSize = minOf(poolSize, DEFAULT_POOL_SIZE, maxReusablePoolFrames),
        discard = ::recycleBitmapOffMain,
        onAvailable = ::signalDecodeCapacity
    )
    private val loopCache = mutableMapOf<SpriteClipId, List<Bitmap>>()
    private val clipPrefetchCache = mutableMapOf<SpriteClipId, ClipPrefetchCacheEntry>()
    private val loopPreloadMutex = Mutex()
    private val headPreloadMutex = Mutex()
    private val closeRequested = AtomicBoolean(false)
    private val residentFrameCount = AtomicInteger(0)
    private val decodeCapacitySignal = Channel<Unit>(Channel.CONFLATED)
    private val releasedBitmaps = CompletableDeferred<Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var playbackJob: Job? = null
    private var generation = 0L
    private var cacheGeneration = 0L
    private var currentBitmap: Bitmap? = null
    private var currentBitmapIsCached = false
    private var currentAtlasFrameIndex: Int? = null
    private var nextPresentationNanos: Long? = null
    private var handoffPresentationNanos: Long? = null
    private var currentClipId: SpriteClipId? = null
    private var currentPresentedClipId: SpriteClipId? = null
    private var currentClipOffset: Int = 0
    private var currentPlaybackReverse = false
    private var currentPlaybackHasPresentedAnchor = false

    init {
        require(startupPrefetchFrames > 0)
        require(queueCapacity > 0)
        require(poolSize > 0)
        require(replacementFrameByteReserve in 1..MAX_RESIDENT_BITMAP_BYTES)
    }

    @Volatile
    override var state: SpritePlaybackState = SpritePlaybackState.UNINITIALIZED
        private set

    override suspend fun prepare(initialClipId: SpriteClipId, presentFrame: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            check(!closeRequested.get() && state == SpritePlaybackState.UNINITIALIZED)
        }
        val clip = manifest.clip(initialClipId)
        val bitmap = withContext(Dispatchers.IO) {
            decodeFrame(clip.startFrame)
        }
        var bitmapHandled = false
        try {
            withContext(Dispatchers.Main.immediate) {
                if (closeRequested.get() || state == SpritePlaybackState.RELEASED) {
                    bitmapPool.release(bitmap)
                } else {
                    if (presentFrame) {
                        replaceCurrent(bitmap, cached = false, frameIndex = clip.startFrame)
                    } else {
                        currentBitmap = bitmap
                        currentBitmapIsCached = false
                        currentAtlasFrameIndex = clip.startFrame
                    }
                    currentClipId = initialClipId
                    currentPresentedClipId = initialClipId
                    currentClipOffset = 0
                    state = SpritePlaybackState.READY
                }
                bitmapHandled = true
            }
        } finally {
            if (!bitmapHandled) bitmapPool.release(bitmap)
        }
    }

    override fun activate() {
        checkMainThread()
        if (closeRequested.get() || state == SpritePlaybackState.RELEASED) return
        check(state == SpritePlaybackState.READY) { "Sprite session must be prepared before activation" }
        currentBitmap?.let(view::swapFrame)
    }

    override suspend fun preloadLoop(clipId: SpriteClipId) {
        loopPreloadMutex.withLock {
            if (closeRequested.get()) return
            val clip = manifest.clip(clipId)
            if (clip.playbackMode != SpritePlaybackMode.LOOP) return
            var requestCacheGeneration = 0L
            val shouldDecode = withContext(Dispatchers.Main.immediate) {
                if (
                    closeRequested.get() ||
                    state == SpritePlaybackState.RELEASED ||
                    clipId in loopCache ||
                    (state == SpritePlaybackState.LOOPING && currentClipId != clipId)
                ) {
                    false
                } else {
                    requestCacheGeneration = cacheGeneration
                    evictLoopCachesExcept(clipId)
                    true
                }
            }
            if (!shouldDecode) return
            val decoded = decodeFrames(clip.startFrame, clip.frameCount)
            var decodedHandled = false
            try {
                withContext(Dispatchers.Main.immediate) {
                    if (
                        closeRequested.get() ||
                        state == SpritePlaybackState.RELEASED ||
                        requestCacheGeneration != cacheGeneration ||
                        clipId in loopCache ||
                        (state == SpritePlaybackState.LOOPING && currentClipId != clipId)
                    ) {
                        recycleOffMain(decoded)
                    } else {
                        replaceLoopCache(clipId, decoded)
                    }
                    decodedHandled = true
                }
            } finally {
                if (!decodedHandled) recycleOffMain(decoded)
            }
        }
    }

    override suspend fun preloadClipHead(clipId: SpriteClipId, frameCount: Int) {
        val activeLoopId = withContext(Dispatchers.Main.immediate) {
            currentClipId.takeIf { state == SpritePlaybackState.LOOPING }
        }
        if (activeLoopId != null) {
            // The provider intentionally serializes region decoding. Finish the current loop cache
            // before prefetching another clip so close prewarming cannot delay the first loop frame.
            preloadLoop(activeLoopId)
        }
        headPreloadMutex.withLock {
            if (closeRequested.get()) return@withLock
            val clip = manifest.clip(clipId)
            val request = withContext(Dispatchers.Main.immediate) {
                if (closeRequested.get() || !isStablePrefetchState()) {
                    null
                } else {
                    ClipPrefetchRequest(
                        clip = clip,
                        startOffset = if (currentAtlasFrameIndex == clip.startFrame) 1 else 0,
                        cacheGeneration = cacheGeneration,
                        playbackGeneration = generation
                    )
                }
            } ?: return@withLock
            preloadClipFramesLocked(request, frameCount)
        }
    }

    private suspend fun preloadClipFramesLocked(
        request: ClipPrefetchRequest,
        frameCount: Int
    ) {
        val availableFrameCount = request.clip.frameCount - request.startOffset
        val desiredCount = frameCount.coerceAtLeast(1)
            .coerceAtMost(availableFrameCount)
            .coerceAtMost(maxTransitionPrefetchFrames)
        if (desiredCount <= 0) return

        val (requestValid, initialEntry) = withContext(Dispatchers.Main.immediate) {
            if (!isCurrentPrefetchRequest(request)) {
                false to null
            } else {
                val cached = clipPrefetchCache[request.clip.id]
                val matchingEntry = if (
                    cached?.startOffset == request.startOffset
                ) {
                    cached
                } else {
                    clipPrefetchCache.remove(request.clip.id)?.frames?.forEach(bitmapPool::release)
                    null
                }
                true to matchingEntry
            }
        }
        if (!requestValid) return
        var entry = initialEntry
        var cachedCount = entry?.frames?.size ?: 0
        while (cachedCount < desiredCount) {
            val batchSize = if (cachedCount == 0) {
                1
            } else {
                minOf(HEAD_PRELOAD_BATCH_SIZE, desiredCount - cachedCount)
            }
            val batchStartOffset = request.startOffset + cachedCount
            val decoded = decodeFrames(
                firstFrameIndex = request.clip.startFrame + batchStartOffset,
                frameCount = batchSize
            )
            var decodedHandled = false
            val accepted = try {
                withContext(Dispatchers.Main.immediate) {
                    val acceptedOnMain = if (!isCurrentPrefetchRequest(request)) {
                        recycleOffMain(decoded)
                        false
                    } else {
                        val currentEntry = clipPrefetchCache[request.clip.id]
                        when {
                            entry == null && currentEntry == null -> {
                                clipPrefetchCache.values.forEach { stale ->
                                    stale.frames.forEach(bitmapPool::release)
                                }
                                clipPrefetchCache.clear()
                                entry = ClipPrefetchCacheEntry(
                                    startOffset = request.startOffset,
                                    frames = decoded.toMutableList()
                                )
                                clipPrefetchCache[request.clip.id] = checkNotNull(entry)
                                true
                            }
                            currentEntry === entry -> {
                                checkNotNull(entry).frames.addAll(decoded)
                                true
                            }
                            else -> {
                                recycleOffMain(decoded)
                                false
                            }
                        }
                    }
                    decodedHandled = true
                    acceptedOnMain
                }
            } finally {
                if (!decodedHandled) recycleOffMain(decoded)
            }
            if (!accepted) return
            cachedCount += decoded.size
        }
    }

    private fun isStablePrefetchState(): Boolean {
        return state == SpritePlaybackState.READY ||
            (
                state == SpritePlaybackState.LOOPING &&
                    currentClipId?.let(loopCache::containsKey) == true
                )
    }

    private fun isCurrentPrefetchRequest(request: ClipPrefetchRequest): Boolean {
        val stateAllowed = state == SpritePlaybackState.READY ||
            (
                state == SpritePlaybackState.LOOPING &&
                    currentClipId?.let(loopCache::containsKey) == true
                )
        return !closeRequested.get() &&
            stateAllowed &&
            request.cacheGeneration == cacheGeneration &&
            request.playbackGeneration == generation
    }

    override fun play(
        clipId: SpriteClipId,
        onComplete: (() -> Unit)?,
        onError: ((Throwable) -> Unit)?
    ) {
        checkMainThread()
        if (closeRequested.get() || state == SpritePlaybackState.RELEASED) return
        startPlayback(
            clipId = clipId,
            reverse = false,
            resumeFromOffset = null,
            onComplete = onComplete,
            onError = onError
        )
    }

    override fun reversePlaybackDirection(
        onComplete: (() -> Unit)?,
        onError: ((Throwable) -> Unit)?
    ): SpriteReverseResult {
        checkMainThread()
        if (closeRequested.get()) return SpriteReverseResult.UNSUPPORTED
        val clipId = currentClipId ?: return SpriteReverseResult.UNSUPPORTED
        val clip = manifest.clip(clipId)
        val result = resolveReversePlaybackResult(
            state = state,
            reversible = clip.reversible,
            playbackClipId = clipId,
            presentedClipId = currentPresentedClipId,
            hasPresentedAnchor = currentPlaybackHasPresentedAnchor
        )
        if (result != SpriteReverseResult.REVERSED) return result
        startPlayback(
            clipId = clipId,
            reverse = !currentPlaybackReverse,
            resumeFromOffset = currentClipOffset,
            onComplete = onComplete,
            onError = onError
        )
        return SpriteReverseResult.REVERSED
    }

    override fun cancelPlayback() {
        checkMainThread()
        if (closeRequested.get() || state == SpritePlaybackState.RELEASED) return
        generation++
        playbackJob?.cancel()
        playbackJob = null
        state = SpritePlaybackState.READY
        currentClipId = currentPresentedClipId
        currentPresentedClipId?.let(manifest::clip)?.let { presentedClip ->
            currentAtlasFrameIndex?.let { frameIndex ->
                currentClipOffset = (frameIndex - presentedClip.startFrame)
                    .coerceIn(0, presentedClip.frameCount - 1)
            }
        }
        currentPlaybackReverse = false
        currentPlaybackHasPresentedAnchor = true
        nextPresentationNanos = null
        handoffPresentationNanos = null
    }

    private fun startPlayback(
        clipId: SpriteClipId,
        reverse: Boolean,
        resumeFromOffset: Int?,
        onComplete: (() -> Unit)?,
        onError: ((Throwable) -> Unit)?
    ) {
        val requestGeneration = ++generation
        val hasPresentedAnchor = resumeFromOffset != null &&
            currentPresentedClipId == clipId &&
            currentAtlasFrameIndex == manifest.clip(clipId).startFrame + resumeFromOffset
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.Main.immediate) {
            val clip = manifest.clip(clipId)
            currentClipId = clipId
            currentPlaybackReverse = reverse
            currentPlaybackHasPresentedAnchor = hasPresentedAnchor
            try {
                when (clip.playbackMode) {
                    SpritePlaybackMode.LOOP -> playLoop(clip)
                    SpritePlaybackMode.ONCE,
                    SpritePlaybackMode.HOLD_LAST -> playOnce(
                        clip = clip,
                        reverse = reverse,
                        resumeFromOffset = resumeFromOffset
                    )
                }
                if (
                    requestGeneration == generation &&
                    !closeRequested.get() &&
                    clip.playbackMode != SpritePlaybackMode.LOOP
                ) {
                    state = SpritePlaybackState.READY
                    onComplete?.invoke()
                    if (requestGeneration == generation) handoffPresentationNanos = null
                }
            } catch (_: CancellationException) {
                // A newer command owns the view and callback generation.
            } catch (error: Exception) {
                if (
                    requestGeneration == generation &&
                    !closeRequested.get() &&
                    state != SpritePlaybackState.RELEASED
                ) {
                    state = SpritePlaybackState.READY
                    nextPresentationNanos = null
                    handoffPresentationNanos = null
                    onError?.invoke(error)
                }
            }
        }
    }

    private suspend fun playOnce(
        clip: SpriteClipSpec,
        reverse: Boolean,
        resumeFromOffset: Int?
    ) = coroutineScope {
        state = SpritePlaybackState.PLAYING_ONCE
        nextPresentationNanos = handoffPresentationNanos
        val firstOffset = resolvePlaybackStartOffset(
            clip = clip,
            currentAtlasFrameIndex = currentAtlasFrameIndex,
            reverse = reverse,
            resumeFromOffset = resumeFromOffset
        )
        val playbackFrameCount = if (reverse) firstOffset + 1 else clip.frameCount - firstOffset
        if (playbackFrameCount <= 0) return@coroutineScope

        val prefetched = ArrayDeque<Bitmap>()
        val cachedPrefetch = clipPrefetchCache.remove(clip.id)
        clipPrefetchCache.values.forEach { stale ->
            stale.frames.forEach(bitmapPool::release)
        }
        clipPrefetchCache.clear()
        val hasUsablePrefetch = !reverse &&
            cachedPrefetch?.startOffset == firstOffset &&
            cachedPrefetch.frames.isNotEmpty()
        if (hasUsablePrefetch) {
            cachedPrefetch?.frames?.forEach(prefetched::addLast)
        } else {
            cachedPrefetch?.frames?.forEach(bitmapPool::release)
        }

        if (prefetched.isEmpty()) {
            val initialCount = minOf(startupPrefetchFrames, playbackFrameCount)
            val firstFrameIndex = frameIndexForOffset(clip, firstOffset)
            decodeFrames(firstFrameIndex, initialCount, direction = if (reverse) -1 else 1)
                .forEach(prefetched::addLast)
        }

        val decodedAheadCount = prefetched.size
        val remainingCount = playbackFrameCount - decodedAheadCount
        val remainingStartOffset = if (reverse) {
            firstOffset - decodedAheadCount
        } else {
            firstOffset + decodedAheadCount
        }
        val residentLoopFrames = loopCache.values.sumOf { it.size }
        val upcomingLoopFrames = clip.nextClipId
            ?.let(manifest::clip)
            ?.takeIf { it.playbackMode == SpritePlaybackMode.LOOP }
            ?.frameCount
            ?: 0
        val reservedLoopFrames = maxOf(
            residentLoopFrames,
            upcomingLoopFrames,
            maxLoopFrameCount
        )
        val effectiveQueueCapacity = resolveTransitionQueueCapacity(
            configuredCapacity = queueCapacity,
            maxCapacityByByteBudget = (
                maxResidentFrameCount -
                    reservedLoopFrames -
                    prefetched.size -
                    CURRENT_FRAME_RESERVE -
                    PRODUCER_PENDING_FRAME_RESERVE
                ).coerceAtLeast(1),
            remainingFrames = remainingCount
        )
        val channel = Channel<Bitmap>(effectiveQueueCapacity)
        val producer = launch(Dispatchers.IO) {
            try {
                repeat(remainingCount) { index ->
                    val offset = if (reverse) {
                        remainingStartOffset - index
                    } else {
                        remainingStartOffset + index
                    }
                    var decoded: Bitmap? = null
                    try {
                        decoded = decodeFrame(clip.startFrame + offset)
                        channel.send(checkNotNull(decoded))
                        decoded = null
                    } finally {
                        decoded?.let(bitmapPool::release)
                    }
                }
            } finally {
                channel.close()
            }
        }

        var playbackOffset = firstOffset
        var pendingBitmap: Bitmap? = null
        try {
            while (prefetched.isNotEmpty()) {
                pendingBitmap = prefetched.removeFirst()
                presentFrame(
                    bitmap = checkNotNull(pendingBitmap),
                    frameIndex = clip.startFrame + playbackOffset,
                    clipOffset = playbackOffset,
                    durationNanos = clip.frameDurationNanos,
                    cached = false
                )
                pendingBitmap = null
                playbackOffset += if (reverse) -1 else 1
            }
            for (bitmap in channel) {
                pendingBitmap = bitmap
                presentFrame(
                    bitmap = bitmap,
                    frameIndex = clip.startFrame + playbackOffset,
                    clipOffset = playbackOffset,
                    durationNanos = clip.frameDurationNanos,
                    cached = false
                )
                pendingBitmap = null
                playbackOffset += if (reverse) -1 else 1
            }
            producer.join()
            awaitClipCompletionBoundary()
        } finally {
            pendingBitmap?.let(bitmapPool::release)
            while (prefetched.isNotEmpty()) bitmapPool.release(prefetched.removeFirst())
            withContext(NonCancellable) {
                producer.cancelAndJoin()
                while (true) {
                    val bitmap = channel.tryReceive().getOrNull() ?: break
                    bitmapPool.release(bitmap)
                }
                channel.cancel()
            }
        }
    }

    private suspend fun playLoop(clip: SpriteClipSpec) {
        state = SpritePlaybackState.LOOPING
        var frames = loopCache[clip.id]
        if (frames == null) {
            handoffPresentationNanos = null
            nextPresentationNanos = null
        } else {
            nextPresentationNanos = handoffPresentationNanos
        }
        while (frames == null) {
            preloadLoop(clip.id)
            currentCoroutineContext().ensureActive()
            frames = loopCache[clip.id]
        }
        val firstOffset = resolveLoopStartOffset(
            clip = clip,
            currentAtlasFrameIndex = currentAtlasFrameIndex,
            currentPresentedClipId = currentPresentedClipId
        )
        while (true) {
            repeat(frames.size) { step ->
                val offset = (firstOffset + step) % frames.size
                val bitmap = frames[offset]
                presentFrame(
                    bitmap = bitmap,
                    frameIndex = clip.startFrame + offset,
                    clipOffset = offset,
                    durationNanos = clip.frameDurationNanos,
                    cached = true
                )
            }
        }
    }

    private suspend fun presentFrame(
        bitmap: Bitmap,
        frameIndex: Int,
        clipOffset: Int,
        durationNanos: Long,
        cached: Boolean
    ) {
        handoffPresentationNanos?.let { handoffNanos ->
            handoffPresentationNanos = null
            currentClipOffset = clipOffset
            currentPresentedClipId = currentClipId
            currentPlaybackHasPresentedAnchor = true
            replaceCurrent(bitmap, cached, frameIndex)
            nextPresentationNanos = handoffNanos + durationNanos
            return
        }
        awaitPresentationSlot(durationNanos)
        currentClipOffset = clipOffset
        currentPresentedClipId = currentClipId
        currentPlaybackHasPresentedAnchor = true
        replaceCurrent(bitmap, cached, frameIndex)
    }

    private suspend fun awaitClipCompletionBoundary() {
        val target = nextPresentationNanos ?: return
        var frameTime = scheduler.awaitFrameNanos()
        while (frameTime < target) frameTime = scheduler.awaitFrameNanos()
        handoffPresentationNanos = frameTime
    }

    private suspend fun awaitPresentationSlot(durationNanos: Long) {
        var target = nextPresentationNanos
        var frameTime = scheduler.awaitFrameNanos()
        if (target == null) target = frameTime
        while (frameTime < target) frameTime = scheduler.awaitFrameNanos()
        nextPresentationNanos = resolveNextPresentationNanos(
            targetNanos = target,
            frameTimeNanos = frameTime,
            durationNanos = durationNanos
        )
    }

    private fun replaceCurrent(bitmap: Bitmap?, cached: Boolean, frameIndex: Int?) {
        val old = currentBitmap
        val oldCached = currentBitmapIsCached
        view.swapFrame(bitmap)
        currentBitmap = bitmap
        currentBitmapIsCached = cached
        currentAtlasFrameIndex = frameIndex
        if (old != null && old !== bitmap && !oldCached) bitmapPool.release(old)
    }

    private suspend fun decodeFrames(
        firstFrameIndex: Int,
        frameCount: Int,
        direction: Int = 1
    ): MutableList<Bitmap> {
        if (frameCount <= 0) return mutableListOf()
        val decoded = ArrayList<Bitmap>(frameCount)
        try {
            withContext(Dispatchers.IO) {
                repeat(frameCount) { offset ->
                    currentCoroutineContext().ensureActive()
                    decoded += decodeFrame(firstFrameIndex + (offset * direction))
                }
            }
            return decoded
        } catch (error: Throwable) {
            recycleOffMain(decoded)
            throw error
        }
    }

    private suspend fun decodeFrame(frameIndex: Int): Bitmap {
        while (true) {
            val reusable = bitmapPool.acquire()
            if (reusable != null) {
                return decodeOwnedFrame(frameIndex, reusable)
            }
            if (tryReserveResidentFrame()) {
                return decodeOwnedFrame(frameIndex, reusable = null)
            }
            try {
                decodeCapacitySignal.receive()
                currentCoroutineContext().ensureActive()
            } catch (cancelled: CancellationException) {
                // A cancelled waiter must not consume the sole conflated wake-up while capacity
                // is already available to another decoder.
                signalDecodeCapacity()
                throw cancelled
            }
        }
    }

    private suspend fun decodeOwnedFrame(frameIndex: Int, reusable: Bitmap?): Bitmap {
        return try {
            frameProvider.decode(frameIndex, reusable)
        } catch (error: Throwable) {
            releaseResidentFrame()
            throw error
        } finally {
            signalDecodeCapacity()
        }
    }

    private fun tryReserveResidentFrame(): Boolean {
        while (true) {
            val current = residentFrameCount.get()
            if (current >= maxResidentFrameCount) return false
            if (residentFrameCount.compareAndSet(current, current + 1)) return true
        }
    }

    private fun releaseResidentFrame() {
        val remaining = residentFrameCount.decrementAndGet()
        check(remaining >= 0) { "Sprite bitmap ownership count became negative" }
        signalDecodeCapacity()
        completeReleaseIfPossible(remaining)
    }

    private fun signalDecodeCapacity() {
        if (residentFrameCount.get() < maxResidentFrameCount || bitmapPool.hasReusable()) {
            decodeCapacitySignal.trySend(Unit)
        }
    }

    private fun completeReleaseIfPossible(remaining: Int = residentFrameCount.get()) {
        if (closeRequested.get() && remaining == 0) releasedBitmaps.complete(Unit)
    }

    private fun frameIndexForOffset(clip: SpriteClipSpec, offset: Int): Int =
        clip.startFrame + offset

    private fun replaceLoopCache(clipId: SpriteClipId, decoded: List<Bitmap>) {
        evictLoopCachesExcept(clipId)
        loopCache[clipId] = decoded
    }

    private fun evictLoopCachesExcept(retainedClipId: SpriteClipId) {
        val iterator = loopCache.entries.iterator()
        while (iterator.hasNext()) {
            val (cachedClipId, cachedFrames) = iterator.next()
            if (cachedClipId == retainedClipId) continue
            cachedFrames.forEach { bitmap ->
                if (bitmap === currentBitmap) {
                    currentBitmapIsCached = false
                } else {
                    bitmapPool.release(bitmap)
                }
            }
            iterator.remove()
        }
    }

    override fun trimMemory() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(::trimMemory)
            return
        }
        if (closeRequested.get() || state == SpritePlaybackState.RELEASED) return
        if (
            state == SpritePlaybackState.LOOPING &&
            currentClipId?.let(loopCache::containsKey) != true
        ) {
            return
        }
        cacheGeneration++
        val bitmapsToRecycle = ArrayList<Bitmap>()
        val keepLoopClipId = currentClipId.takeIf { state == SpritePlaybackState.LOOPING }
        val loopIterator = loopCache.entries.iterator()
        while (loopIterator.hasNext()) {
            val (clipId, frames) = loopIterator.next()
            if (clipId == keepLoopClipId) continue
            frames.forEach { bitmap ->
                if (bitmap === currentBitmap) {
                    currentBitmapIsCached = false
                } else {
                    bitmapsToRecycle += bitmap
                }
            }
            loopIterator.remove()
        }
        clipPrefetchCache.values.forEach { entry ->
            bitmapsToRecycle.addAll(entry.frames)
        }
        clipPrefetchCache.clear()
        bitmapsToRecycle.addAll(bitmapPool.drain())
        recycleOffMain(bitmapsToRecycle)
    }

    override fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            if (closeRequested.compareAndSet(false, true)) {
                mainHandler.post(::closeOnMain)
            }
            return
        }
        closeRequested.set(true)
        closeOnMain()
    }

    private fun closeOnMain() {
        if (state == SpritePlaybackState.RELEASED) return
        state = SpritePlaybackState.RELEASED
        generation++
        cacheGeneration++
        playbackJob?.cancel()
        playbackJob = null

        val ownedCurrent = currentBitmap
        val bitmapsToRecycle = ArrayList<Bitmap>()
        try {
            if (view.currentFrame() === ownedCurrent) view.swapFrame(null)
        } finally {
            try {
                loopCache.values.forEach { frames ->
                    frames.forEach { bitmap ->
                        if (bitmap !== ownedCurrent) bitmapsToRecycle += bitmap
                    }
                }
                loopCache.clear()
                clipPrefetchCache.values.forEach { entry ->
                    entry.frames.forEach { bitmap ->
                        if (bitmap !== ownedCurrent) bitmapsToRecycle += bitmap
                    }
                }
                clipPrefetchCache.clear()
            } finally {
                currentBitmap = null
                currentBitmapIsCached = false
                currentAtlasFrameIndex = null
                currentPresentedClipId = null
                currentPlaybackReverse = false
                currentPlaybackHasPresentedAnchor = false
                nextPresentationNanos = null
                handoffPresentationNanos = null
                frameProvider.close()
                bitmapsToRecycle.addAll(bitmapPool.closeAndDrain())
                recycleOffMain(bitmapsToRecycle)
                ownedCurrent?.let(::recycleDisplayedBitmapAfterHandoff)
                completeReleaseIfPossible()
            }
        }
    }

    override suspend fun awaitReleased() {
        releasedBitmaps.await()
    }

    private fun recycleOffMain(bitmaps: Collection<Bitmap>) {
        if (bitmaps.isEmpty()) return
        val owned = bitmaps.toList()
        Dispatchers.Default.dispatch(EmptyCoroutineContext) {
            owned.forEach { bitmap ->
                try {
                    bitmap.recycleSafely()
                } finally {
                    releaseResidentFrame()
                }
            }
        }
    }

    private fun recycleBitmapOffMain(bitmap: Bitmap) {
        Dispatchers.Default.dispatch(EmptyCoroutineContext) {
            try {
                bitmap.recycleSafely()
            } finally {
                releaseResidentFrame()
            }
        }
    }

    private fun recycleDisplayedBitmapAfterHandoff(bitmap: Bitmap) {
        mainHandler.postDelayed(
            { recycleBitmapOffMain(bitmap) },
            DISPLAYED_BITMAP_RECYCLE_DELAY_MILLIS
        )
    }

    private data class ClipPrefetchRequest(
        val clip: SpriteClipSpec,
        val startOffset: Int,
        val cacheGeneration: Long,
        val playbackGeneration: Long
    )

    private data class ClipPrefetchCacheEntry(
        val startOffset: Int,
        val frames: MutableList<Bitmap>
    )

    companion object {
        private const val HEAD_PRELOAD_BATCH_SIZE = 3
        private const val DEFAULT_POOL_SIZE = 12
        private const val CURRENT_FRAME_RESERVE = 1
        private const val PRODUCER_PENDING_FRAME_RESERVE = 1
        private const val ARGB_8888_BYTES_PER_PIXEL = 4L
        private const val MAX_RESIDENT_BITMAP_BYTES = 18L * 1_024L * 1_024L
        private const val DISPLAYED_BITMAP_RECYCLE_DELAY_MILLIS = 32L
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Sprite playback commands must run on the main thread"
        }
    }
}

internal fun resolveTransitionQueueCapacity(
    configuredCapacity: Int,
    maxCapacityByByteBudget: Int,
    remainingFrames: Int
): Int {
    require(configuredCapacity > 0)
    require(maxCapacityByByteBudget > 0)
    return configuredCapacity
        .coerceAtMost(maxCapacityByByteBudget)
        .coerceAtMost(remainingFrames.coerceAtLeast(1))
}

internal fun resolvePlaybackStartOffset(
    clip: SpriteClipSpec,
    currentAtlasFrameIndex: Int?,
    reverse: Boolean,
    resumeFromOffset: Int?
): Int {
    if (resumeFromOffset != null) {
        val requestedOffset = resumeFromOffset.coerceIn(0, clip.frameCount - 1)
        return if (currentAtlasFrameIndex == clip.startFrame + requestedOffset) {
            requestedOffset + if (reverse) -1 else 1
        } else {
            requestedOffset
        }
    }
    if (reverse) {
        val requestedOffset = clip.frameCount - 1
        return if (currentAtlasFrameIndex == clip.startFrame + requestedOffset) {
            requestedOffset - 1
        } else {
            requestedOffset
        }
    }
    return if (currentAtlasFrameIndex == clip.startFrame) 1 else 0
}

internal fun resolveLoopStartOffset(
    clip: SpriteClipSpec,
    currentAtlasFrameIndex: Int?,
    currentPresentedClipId: SpriteClipId?
): Int {
    if (currentPresentedClipId != clip.id) return 0
    val currentOffset = currentAtlasFrameIndex?.minus(clip.startFrame) ?: return 0
    if (currentOffset !in 0 until clip.frameCount) return 0
    return (currentOffset + 1) % clip.frameCount
}

internal fun resolveNextPresentationNanos(
    targetNanos: Long,
    frameTimeNanos: Long,
    durationNanos: Long
): Long {
    require(durationNanos > 0L)
    require(frameTimeNanos >= targetNanos)
    return if (frameTimeNanos - targetNanos >= durationNanos) {
        frameTimeNanos + durationNanos
    } else {
        targetNanos + durationNanos
    }
}

internal fun resolveReversePlaybackResult(
    state: SpritePlaybackState,
    reversible: Boolean,
    playbackClipId: SpriteClipId?,
    presentedClipId: SpriteClipId?,
    hasPresentedAnchor: Boolean
): SpriteReverseResult {
    if (
        state != SpritePlaybackState.PLAYING_ONCE ||
        !reversible ||
        playbackClipId == null
    ) {
        return SpriteReverseResult.UNSUPPORTED
    }
    return if (playbackClipId == presentedClipId && hasPresentedAnchor) {
        SpriteReverseResult.REVERSED
    } else {
        SpriteReverseResult.NOT_STARTED
    }
}

internal fun resolveSessionResidentFrameLimit(
    maxResidentBitmapBytes: Long,
    frameByteCount: Long,
    replacementFrameByteReserve: Long = frameByteCount
): Int {
    require(maxResidentBitmapBytes > 0L)
    require(frameByteCount > 0L)
    require(replacementFrameByteReserve in 1..maxResidentBitmapBytes)
    require(frameByteCount + replacementFrameByteReserve <= maxResidentBitmapBytes) {
        "Current and replacement frames cannot fit in the animation bitmap budget"
    }
    // Keep room for the largest replacement frame allowed by the consuming feature.
    return ((maxResidentBitmapBytes - replacementFrameByteReserve) / frameByteCount)
        .toInt()
        .coerceAtLeast(1)
}

private const val CACHED_TRANSITION_QUEUE_CAPACITY = 3

interface SpriteSessionFactory {
    suspend fun create(
        pack: SpritePack,
        view: SpriteAnimationView,
        scope: CoroutineScope
    ): SpritePlaybackSession
}

class DefaultSpriteSessionFactory(
    private val replacementFrameByteReserve: Long? = null
) : SpriteSessionFactory {
    override suspend fun create(
        pack: SpritePack,
        view: SpriteAnimationView,
        scope: CoroutineScope
    ): SpritePlaybackSession {
        SpritePackValidator().validate(pack.manifest)
        var pendingProvider: BitmapRegionSpriteFrameProvider? = null
        try {
            withContext(Dispatchers.IO) {
                pendingProvider = BitmapRegionSpriteFrameProvider(
                    pack.manifest.atlas,
                    pack.atlasSource
                )
            }
            val provider = checkNotNull(pendingProvider)
            val session = withContext(Dispatchers.Main.immediate) {
                DefaultSpritePlaybackSession(
                    manifest = pack.manifest,
                    view = view,
                    frameProvider = provider,
                    scheduler = ChoreographerAnimationScheduler(),
                    scope = scope,
                    replacementFrameByteReserve = replacementFrameByteReserve ?: (
                        pack.manifest.atlas.frameWidth.toLong() *
                            pack.manifest.atlas.frameHeight *
                            ARGB_8888_BYTES_PER_PIXEL
                        )
                )
            }
            pendingProvider = null
            return session
        } finally {
            pendingProvider?.close()
        }
    }

    private companion object {
        const val ARGB_8888_BYTES_PER_PIXEL = 4L
    }
}

private class BitmapPool(
    private val maxSize: Int,
    private val discard: (Bitmap) -> Unit,
    private val onAvailable: () -> Unit
) {
    private val bitmaps = ArrayDeque<Bitmap>()
    private var closed = false

    @Synchronized
    fun acquire(): Bitmap? {
        if (closed || bitmaps.isEmpty()) return null
        return bitmaps.removeFirst()
    }

    @Synchronized
    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        if (closed) {
            discard(bitmap)
        } else {
            if (bitmaps.size >= maxSize) discard(bitmaps.removeFirst())
            bitmaps.addLast(bitmap)
            onAvailable()
        }
    }

    @Synchronized
    fun hasReusable(): Boolean = !closed && bitmaps.isNotEmpty()

    @Synchronized
    fun drain(): List<Bitmap> {
        val drained = ArrayList<Bitmap>(bitmaps.size)
        while (bitmaps.isNotEmpty()) drained += bitmaps.removeFirst()
        return drained
    }

    @Synchronized
    fun closeAndDrain(): List<Bitmap> {
        if (closed) return emptyList()
        closed = true
        return drain()
    }
}

private fun Bitmap.recycleSafely() {
    if (!isRecycled) recycle()
}
