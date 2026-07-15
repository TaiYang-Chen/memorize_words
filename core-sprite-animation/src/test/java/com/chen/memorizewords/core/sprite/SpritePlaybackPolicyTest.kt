package com.chen.memorizewords.core.sprite

import kotlin.test.Test
import kotlin.test.assertEquals

class SpritePlaybackPolicyTest {
    private val openClip = SpriteClipSpec(
        id = SpriteClipId("card_open"),
        startFrame = 0,
        frameCount = 25,
        framesPerSecond = 24,
        playbackMode = SpritePlaybackMode.ONCE,
        reversible = true
    )

    @Test
    fun `cached transitions reserve producer memory and cap the queue to three frames`() {
        assertEquals(
            3,
            resolveTransitionQueueCapacity(
                configuredCapacity = 12,
                maxCapacityByByteBudget = 3,
                remainingFrames = 13
            )
        )
    }

    @Test
    fun `uncached transitions retain throughput without exceeding remaining frames`() {
        assertEquals(
            12,
            resolveTransitionQueueCapacity(
                configuredCapacity = 12,
                maxCapacityByByteBudget = 12,
                remainingFrames = 24
            )
        )
        assertEquals(
            2,
            resolveTransitionQueueCapacity(
                configuredCapacity = 12,
                maxCapacityByByteBudget = 12,
                remainingFrames = 2
            )
        )
    }

    @Test
    fun `byte budget can reduce an otherwise uncached queue`() {
        assertEquals(
            2,
            resolveTransitionQueueCapacity(
                configuredCapacity = 12,
                maxCapacityByByteBudget = 2,
                remainingFrames = 24
            )
        )
    }

    @Test
    fun `an already displayed first frame advances without dropping a source frame`() {
        assertEquals(
            1,
            resolvePlaybackStartOffset(
                clip = openClip,
                currentAtlasFrameIndex = 0,
                reverse = false,
                resumeFromOffset = null
            )
        )
        assertEquals(
            0,
            resolvePlaybackStartOffset(
                clip = openClip,
                currentAtlasFrameIndex = 24,
                reverse = false,
                resumeFromOffset = null
            )
        )
    }

    @Test
    fun `reverse playback continues before the frame already on screen`() {
        assertEquals(
            6,
            resolvePlaybackStartOffset(
                clip = openClip,
                currentAtlasFrameIndex = 7,
                reverse = true,
                resumeFromOffset = 7
            )
        )
        assertEquals(
            -1,
            resolvePlaybackStartOffset(
                clip = openClip,
                currentAtlasFrameIndex = 0,
                reverse = true,
                resumeFromOffset = 0
            )
        )
    }

    @Test
    fun `reversing a reverse playback resumes forward after the displayed frame`() {
        assertEquals(
            8,
            resolvePlaybackStartOffset(
                clip = openClip,
                currentAtlasFrameIndex = 7,
                reverse = false,
                resumeFromOffset = 7
            )
        )
    }

    @Test
    fun `reverse reports not started until the requested clip owns a displayed frame`() {
        assertEquals(
            SpriteReverseResult.NOT_STARTED,
            resolveReversePlaybackResult(
                state = SpritePlaybackState.PLAYING_ONCE,
                reversible = true,
                playbackClipId = SpriteClipId("card_close"),
                presentedClipId = SpriteClipId("card_visible"),
                hasPresentedAnchor = false
            )
        )
        assertEquals(
            SpriteReverseResult.REVERSED,
            resolveReversePlaybackResult(
                state = SpritePlaybackState.PLAYING_ONCE,
                reversible = true,
                playbackClipId = SpriteClipId("card_close"),
                presentedClipId = SpriteClipId("card_close"),
                hasPresentedAnchor = true
            )
        )
    }

    @Test
    fun `completed transition cannot be reversed after reaching a stable state`() {
        assertEquals(
            SpriteReverseResult.UNSUPPORTED,
            resolveReversePlaybackResult(
                state = SpritePlaybackState.READY,
                reversible = true,
                playbackClipId = SpriteClipId("card_close"),
                presentedClipId = SpriteClipId("card_close"),
                hasPresentedAnchor = true
            )
        )
    }

    @Test
    fun `session budget reserves one frame for atomic pack replacement`() {
        val frameBytes = 336L * 336L * 4L
        val maxBytes = 18L * 1_024L * 1_024L
        val sessionFrames = resolveSessionResidentFrameLimit(maxBytes, frameBytes)

        assertEquals(40, sessionFrames)
        assertEquals(true, (sessionFrames + 1L) * frameBytes <= maxBytes)
    }

    @Test
    fun `session budget can reserve a larger feature replacement frame`() {
        val oldFrameBytes = 128L * 128L * 4L
        val replacementFrameBytes = 336L * 336L * 4L
        val maxBytes = 18L * 1_024L * 1_024L
        val sessionFrames = resolveSessionResidentFrameLimit(
            maxResidentBitmapBytes = maxBytes,
            frameByteCount = oldFrameBytes,
            replacementFrameByteReserve = replacementFrameBytes
        )

        assertEquals(true, sessionFrames * oldFrameBytes + replacementFrameBytes <= maxBytes)
    }

    @Test
    fun `presentation timing keeps the absolute 24 fps clock unless a full frame is lost`() {
        val frameDuration = 1_000_000_000L / 24L

        assertEquals(
            frameDuration,
            resolveNextPresentationNanos(
                targetNanos = 0L,
                frameTimeNanos = 16_666_667L,
                durationNanos = frameDuration
            )
        )
        assertEquals(
            50_000_000L + frameDuration,
            resolveNextPresentationNanos(
                targetNanos = 0L,
                frameTimeNanos = 50_000_000L,
                durationNanos = frameDuration
            )
        )
        assertEquals(
            frameDuration * 2L,
            resolveNextPresentationNanos(
                targetNanos = 0L,
                frameTimeNanos = frameDuration,
                durationNanos = frameDuration
            )
        )
    }

    @Test
    fun `loop resumes after the frame already displayed`() {
        val loop = SpriteClipSpec(
            id = SpriteClipId("loop"),
            startFrame = 25,
            frameCount = 24,
            framesPerSecond = 24,
            playbackMode = SpritePlaybackMode.LOOP
        )

        assertEquals(
            8,
            resolveLoopStartOffset(
                clip = loop,
                currentAtlasFrameIndex = 32,
                currentPresentedClipId = loop.id
            )
        )
        assertEquals(
            0,
            resolveLoopStartOffset(
                clip = loop,
                currentAtlasFrameIndex = 24,
                currentPresentedClipId = loop.id
            )
        )
    }

    @Test
    fun `loop starts at its first frame when another overlapping clip owns the display`() {
        val loop = SpriteClipSpec(
            id = SpriteClipId("loop"),
            startFrame = 25,
            frameCount = 24,
            framesPerSecond = 24,
            playbackMode = SpritePlaybackMode.LOOP
        )

        assertEquals(
            0,
            resolveLoopStartOffset(
                clip = loop,
                currentAtlasFrameIndex = 32,
                currentPresentedClipId = SpriteClipId("overlapping_transition")
            )
        )
    }
}
