package com.chen.memorizewords.core.sprite

import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

internal class GpuSpritePlaybackSession(
    override val manifest: SpritePackManifest,
    private val assets: Map<SpriteTextureId, Ktx2TextureAsset>,
    private val residentTextureIds: Set<SpriteTextureId>,
    private val host: FloatingPetRenderHost,
    private val view: GpuSpriteTextureView
) : SpritePlaybackSession {
    private val closeRequested = AtomicBoolean(false)
    private val removed = AtomicBoolean(false)
    private var generation = 0L
    private var currentClipId: SpriteClipId? = null
    private var currentReverse = false

    @Volatile
    override var state: SpritePlaybackState = SpritePlaybackState.UNINITIALIZED
        private set

    override suspend fun prepare(initialClipId: SpriteClipId, presentFrame: Boolean) {
        checkMainThread()
        check(!closeRequested.get() && state == SpritePlaybackState.UNINITIALIZED)
        val initialClip = manifest.clip(initialClipId)
        view.prepare(
            assets = assets,
            residentTextureIds = residentTextureIds,
            initialClip = initialClip,
            presentFrame = presentFrame
        )
        if (closeRequested.get()) return
        currentClipId = initialClipId
        currentReverse = false
        state = SpritePlaybackState.READY
    }

    override fun activate() {
        checkMainThread()
        if (closeRequested.get() || state == SpritePlaybackState.RELEASED) return
        check(state == SpritePlaybackState.READY) { "GPU sprite session is not prepared" }
        activateRenderer(host, view)
        view.activate()
    }

    override suspend fun preloadClipHead(clipId: SpriteClipId, frameCount: Int) {
        require(frameCount > 0)
        if (closeRequested.get() || state == SpritePlaybackState.RELEASED) return
        val clip = manifest.clip(clipId)
        view.preloadTexture(clip.textureId)
    }

    override suspend fun preloadLoop(clipId: SpriteClipId) {
        if (closeRequested.get() || state == SpritePlaybackState.RELEASED) return
        val clip = manifest.clip(clipId)
        view.preloadTexture(clip.textureId)
    }

    override fun play(
        clipId: SpriteClipId,
        onComplete: (() -> Unit)?,
        onError: ((Throwable) -> Unit)?
    ) {
        checkMainThread()
        if (closeRequested.get() || state == SpritePlaybackState.RELEASED) return
        val clip = manifest.clip(clipId)
        val requestGeneration = ++generation
        currentClipId = clipId
        currentReverse = false
        state = if (clip.playbackMode == SpritePlaybackMode.LOOP) {
            SpritePlaybackState.LOOPING
        } else {
            SpritePlaybackState.PLAYING_ONCE
        }
        view.play(
            clip = clip,
            generation = requestGeneration,
            onComplete = { completedGeneration ->
                if (completedGeneration != generation || closeRequested.get()) return@play
                state = SpritePlaybackState.READY
                onComplete?.invoke()
            },
            onError = { failedGeneration, error ->
                if (failedGeneration != generation || closeRequested.get()) return@play
                state = SpritePlaybackState.READY
                onError?.invoke(error)
            }
        )
    }

    override fun reversePlaybackDirection(
        onComplete: (() -> Unit)?,
        onError: ((Throwable) -> Unit)?
    ): SpriteReverseResult {
        checkMainThread()
        if (closeRequested.get() || state != SpritePlaybackState.PLAYING_ONCE) {
            return SpriteReverseResult.UNSUPPORTED
        }
        val clipId = currentClipId ?: return SpriteReverseResult.UNSUPPORTED
        val clip = manifest.clip(clipId)
        if (!clip.reversible) return SpriteReverseResult.UNSUPPORTED
        val requestGeneration = ++generation
        currentReverse = !currentReverse
        view.reverse(
            generation = requestGeneration,
            onComplete = { completedGeneration ->
                if (completedGeneration != generation || closeRequested.get()) return@reverse
                state = SpritePlaybackState.READY
                onComplete?.invoke()
            },
            onError = { failedGeneration, error ->
                if (failedGeneration != generation || closeRequested.get()) return@reverse
                state = SpritePlaybackState.READY
                onError?.invoke(error)
            }
        )
        return SpriteReverseResult.REVERSED
    }

    override fun cancelPlayback() {
        checkMainThread()
        if (closeRequested.get() || state == SpritePlaybackState.RELEASED) return
        generation++
        view.cancelPlayback()
        state = SpritePlaybackState.READY
    }

    override fun trimMemory() {
        if (closeRequested.get() || state == SpritePlaybackState.RELEASED) return
        view.trimMemory()
    }

    override suspend fun trimMemoryAndAwait() {
        if (closeRequested.get() || state == SpritePlaybackState.RELEASED) return
        view.trimMemoryAndAwait()
    }

    override fun close() {
        if (!closeRequested.compareAndSet(false, true)) return
        generation++
        state = SpritePlaybackState.RELEASED
        try {
            view.close()
        } finally {
            removeRendererOnMain(host, view, removed)
        }
    }

    override suspend fun awaitReleased() {
        view.awaitReleased()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "GPU sprite playback commands must run on the main thread"
        }
    }
}
