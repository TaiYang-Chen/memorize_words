package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

import com.chen.memorizewords.core.sprite.SpriteClipId
import com.chen.memorizewords.core.sprite.SpritePackManifest
import com.chen.memorizewords.core.sprite.SpritePlaybackSession
import com.chen.memorizewords.core.sprite.SpritePlaybackState
import com.chen.memorizewords.core.sprite.SpriteReverseResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingPetReloadPolicyTest {
    @Test
    fun `replacement waits until the active session trim barrier completes`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val session = BarrierSpriteSession(entered, release)
        val replacement = launch { trimFloatingPetSessionBeforeReplacement(session) }
        entered.await()
        assertFalse(replacement.isCompleted)
        release.complete(Unit)
        replacement.join()
        assertTrue(session.trimCompleted)
    }

    @Test
    fun `same installed version reuses the active session for a normal switch`() {
        assertTrue(
            shouldReuseFloatingPetSession(
                forceReload = false,
                hasCurrentSession = true,
                sameView = true,
                samePackId = true,
                samePackVersion = true
            )
        )
    }

    @Test
    fun `forced reload replaces the active session even for the same version`() {
        assertFalse(
            shouldReuseFloatingPetSession(
                forceReload = true,
                hasCurrentSession = true,
                sameView = true,
                samePackId = true,
                samePackVersion = true
            )
        )
    }
}

private class BarrierSpriteSession(
    private val entered: CompletableDeferred<Unit>,
    private val release: CompletableDeferred<Unit>
) : SpritePlaybackSession {
    override val manifest: SpritePackManifest
        get() = error("unused")
    override val state: SpritePlaybackState = SpritePlaybackState.READY
    var trimCompleted: Boolean = false
        private set

    override suspend fun prepare(initialClipId: SpriteClipId, presentFrame: Boolean) = Unit

    override fun activate() = Unit

    override suspend fun preloadClipHead(clipId: SpriteClipId, frameCount: Int) = Unit

    override suspend fun preloadLoop(clipId: SpriteClipId) = Unit

    override fun play(
        clipId: SpriteClipId,
        onComplete: (() -> Unit)?,
        onError: ((Throwable) -> Unit)?
    ) = Unit

    override fun reversePlaybackDirection(
        onComplete: (() -> Unit)?,
        onError: ((Throwable) -> Unit)?
    ): SpriteReverseResult = SpriteReverseResult.UNSUPPORTED

    override fun cancelPlayback() = Unit

    override fun trimMemory() = Unit

    override suspend fun trimMemoryAndAwait() {
        entered.complete(Unit)
        release.await()
        trimCompleted = true
    }

    override suspend fun awaitReleased() = Unit

    override fun close() = Unit
}
