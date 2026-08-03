package com.chen.memorizewords.core.sprite

import java.io.Closeable

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
    suspend fun trimMemoryAndAwait() {
        trimMemory()
    }
    suspend fun awaitReleased()
}
