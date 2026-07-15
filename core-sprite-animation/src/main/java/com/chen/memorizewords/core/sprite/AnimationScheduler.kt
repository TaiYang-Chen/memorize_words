package com.chen.memorizewords.core.sprite

import android.view.Choreographer
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

fun interface AnimationScheduler {
    suspend fun awaitFrameNanos(): Long
}

class ChoreographerAnimationScheduler(
    private val choreographer: Choreographer = Choreographer.getInstance()
) : AnimationScheduler {
    override suspend fun awaitFrameNanos(): Long = suspendCancellableCoroutine { continuation ->
        val callback = Choreographer.FrameCallback { frameTimeNanos ->
            if (continuation.isActive) continuation.resume(frameTimeNanos)
        }
        continuation.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
        choreographer.postFrameCallback(callback)
    }
}
