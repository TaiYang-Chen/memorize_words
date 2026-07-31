package com.chen.memorizewords.feature.floatingreview.ui.floating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingCardFrameDispatcherTest {
    @Test
    fun `animation frames update only monotonically changing surface height`() {
        val window = FakeWindowPort()
        val surface = RecordingSurfacePort()
        val dispatcher = FloatingCardFrameDispatcher(window, surface)
        window.attach()
        dispatcher.attach()
        val transaction = dispatcher.beginTransaction()
        val transition = snapshot(windowHeight = 320, surfaceHeight = 220)

        assertTrue(dispatcher.prepare(transaction, transition))
        val windowUpdatesBeforeAnimation = window.updates.size
        listOf(220, 232, 251, 279, 305, 320).forEach { height ->
            assertTrue(
                dispatcher.animateFrame(
                    transaction = transaction,
                    height = height,
                    canvasHeight = 320,
                    placement = FloatingSpeechPlacement.ABOVE_PET
                )
            )
        }

        assertEquals(windowUpdatesBeforeAnimation, window.updates.size)
        val animatedHeights = surface.updates.takeLast(6).map(SurfaceUpdate::height)
        assertTrue(animatedHeights.zipWithNext().all { (first, second) -> second >= first })

        assertTrue(dispatcher.normalize(transaction, snapshot(320, 320)))
        assertEquals(windowUpdatesBeforeAnimation + 1, window.updates.size)
    }

    @Test
    fun `stale and cancelled transactions cannot update surface or window`() {
        val window = FakeWindowPort()
        val surface = RecordingSurfacePort()
        val dispatcher = FloatingCardFrameDispatcher(window, surface)
        window.attach()
        dispatcher.attach()
        val stale = dispatcher.beginTransaction()
        assertTrue(dispatcher.prepare(stale, snapshot(320, 220)))

        val current = dispatcher.beginTransaction()
        val windowCount = window.updates.size
        val surfaceCount = surface.updates.size
        assertFalse(
            dispatcher.animateFrame(
                stale,
                height = 240,
                canvasHeight = 320,
                placement = FloatingSpeechPlacement.ABOVE_PET
            )
        )
        assertFalse(dispatcher.normalize(stale, snapshot(240, 240)))
        assertEquals(windowCount, window.updates.size)
        assertEquals(surfaceCount, surface.updates.size)

        dispatcher.invalidate()
        assertFalse(
            dispatcher.animateFrame(
                current,
                height = 260,
                canvasHeight = 320,
                placement = FloatingSpeechPlacement.ABOVE_PET
            )
        )
        assertEquals(surfaceCount, surface.updates.size)
    }

    @Test
    fun `removed window rejects every later transaction and anchor move`() {
        val window = FakeWindowPort()
        val surface = RecordingSurfacePort()
        val dispatcher = FloatingCardFrameDispatcher(window, surface)
        window.attach()
        dispatcher.attach()
        val transaction = dispatcher.beginTransaction()
        dispatcher.detach()
        window.detach()

        assertFalse(dispatcher.prepare(transaction, snapshot(320, 220)))
        assertFalse(
            dispatcher.animateFrame(
                transaction,
                height = 240,
                canvasHeight = 320,
                placement = FloatingSpeechPlacement.ABOVE_PET
            )
        )
        assertFalse(dispatcher.normalize(transaction, snapshot(240, 240)))
        assertFalse(dispatcher.moveWindow(FloatingCardWindowFrame(0, 0, 320, 240)))
        assertTrue(window.updates.isEmpty())
        assertTrue(surface.updates.isEmpty())
    }

    private fun snapshot(windowHeight: Int, surfaceHeight: Int): FloatingCardLayoutSnapshot {
        return FloatingCardLayoutSnapshot(
            placement = FloatingSpeechPlacement.ABOVE_PET,
            window = FloatingCardWindowFrame(x = 20, y = 100, width = 320, height = windowHeight),
            surface = FloatingCardSurfaceFrame(
                top = windowHeight - surfaceHeight,
                width = 320,
                height = surfaceHeight
            ),
            surfaceScreenY = 100 + windowHeight - surfaceHeight,
            tailCenterX = 160,
            targetHeight = surfaceHeight
        )
    }

    private class FakeWindowPort : FloatingCardWindowPort {
        private var currentFrame = FloatingCardWindowFrame(0, 0, 1, 1)
        val updates = mutableListOf<FloatingCardWindowFrame>()

        override var attached: Boolean = false
            private set

        override val frame: FloatingCardWindowFrame
            get() = currentFrame

        override fun attach() {
            attached = true
        }

        override fun update(frame: FloatingCardWindowFrame) {
            check(attached)
            currentFrame = frame
            updates += frame
        }

        override fun detach() {
            attached = false
        }
    }

    private data class SurfaceUpdate(
        val height: Int,
        val canvasHeight: Int,
        val placement: FloatingSpeechPlacement
    )

    private class RecordingSurfacePort : FloatingCardSurfacePort {
        val updates = mutableListOf<SurfaceUpdate>()

        override fun update(
            height: Int,
            canvasHeight: Int,
            placement: FloatingSpeechPlacement
        ) {
            updates += SurfaceUpdate(height, canvasHeight, placement)
        }
    }
}
