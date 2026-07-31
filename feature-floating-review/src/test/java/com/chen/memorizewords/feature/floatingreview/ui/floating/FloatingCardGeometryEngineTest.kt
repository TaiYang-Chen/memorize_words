package com.chen.memorizewords.feature.floatingreview.ui.floating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloatingCardGeometryEngineTest {
    private val engine = FloatingCardGeometryEngine()
    private val safeArea = FloatingSpeechSafeArea(left = 0, top = 24, right = 360, bottom = 760)
    private val config = FloatingSpeechLayoutConfig(
        edgeMarginPx = 12,
        clearancePx = 40,
        tailWidthPx = 34,
        tailSafeInsetPx = 24,
        tailSlotHeightPx = 18
    )

    @Test
    fun `expanding transition preserves old and target card screen rectangles`() {
        listOf(
            FloatingSpeechPlacement.ABOVE_PET to FloatingSpeechPetBounds(80, 430, 156, 187),
            FloatingSpeechPlacement.BELOW_PET to FloatingSpeechPetBounds(80, 60, 156, 187)
        ).forEach { (placement, pet) ->
            val oldInput = input(pet = pet, naturalHeight = 220)
            val targetInput = input(pet = pet, naturalHeight = 320)
            val oldStable = engine.resolveStable(oldInput, placement)
            val targetStable = engine.resolveStable(targetInput, placement)
            val transitionStart = engine.resolveTransition(
                input = targetInput,
                placement = placement,
                visualHeight = oldStable.targetHeight,
                targetHeight = targetStable.targetHeight
            )

            assertEquals(oldStable.surfaceScreenY, transitionStart.surfaceScreenY)
            assertEquals(oldStable.surface.height, transitionStart.surface.height)

            val transitionEndTop = transitionStart.window.y + engine.surfaceTop(
                canvasHeight = transitionStart.window.height,
                visualHeight = targetStable.targetHeight,
                placement = placement
            )
            assertEquals(targetStable.surfaceScreenY, transitionEndTop)
            assertEquals(targetStable.window.width, transitionStart.window.width)
        }
    }

    @Test
    fun `shrinking transition preserves old and target card screen rectangles`() {
        listOf(
            FloatingSpeechPlacement.ABOVE_PET to FloatingSpeechPetBounds(80, 430, 156, 187),
            FloatingSpeechPlacement.BELOW_PET to FloatingSpeechPetBounds(80, 60, 156, 187)
        ).forEach { (placement, pet) ->
            val oldInput = input(pet = pet, naturalHeight = 320)
            val targetInput = input(pet = pet, naturalHeight = 220)
            val oldStable = engine.resolveStable(oldInput, placement)
            val targetStable = engine.resolveStable(targetInput, placement)
            val transitionStart = engine.resolveTransition(
                input = targetInput,
                placement = placement,
                visualHeight = oldStable.targetHeight,
                targetHeight = targetStable.targetHeight
            )

            assertEquals(oldStable.surfaceScreenY, transitionStart.surfaceScreenY)
            assertEquals(oldStable.surface.height, transitionStart.surface.height)

            val transitionEndTop = transitionStart.window.y + engine.surfaceTop(
                canvasHeight = transitionStart.window.height,
                visualHeight = targetStable.targetHeight,
                placement = placement
            )
            assertEquals(targetStable.surfaceScreenY, transitionEndTop)
        }
    }

    @Test
    fun `above card panel bottom is fixed for every animation frame and gap`() {
        listOf(40, 0, -20).forEach { gap ->
            val pet = FloatingSpeechPetBounds(80, 430, 156, 187)
            val transition = engine.resolveTransition(
                input = input(pet, naturalHeight = 320, gap = gap),
                placement = FloatingSpeechPlacement.ABOVE_PET,
                visualHeight = 220,
                targetHeight = 320
            )
            val movingTops = (220..320 step 5).map { height ->
                val surfaceTop = engine.surfaceTop(
                    transition.window.height,
                    height,
                    FloatingSpeechPlacement.ABOVE_PET
                )
                val surfaceScreenTop = transition.window.y + surfaceTop
                assertEquals(
                    pet.y - gap,
                    surfaceScreenTop + height - config.tailSlotHeightPx
                )
                surfaceScreenTop
            }

            assertTrue(movingTops.zipWithNext().all { (first, second) -> second <= first })
        }
    }

    @Test
    fun `below card panel top is fixed for every animation frame and gap`() {
        listOf(40, 0, -20).forEach { gap ->
            val pet = FloatingSpeechPetBounds(80, 60, 156, 187)
            val transition = engine.resolveTransition(
                input = input(pet, naturalHeight = 320, gap = gap),
                placement = FloatingSpeechPlacement.BELOW_PET,
                visualHeight = 220,
                targetHeight = 320
            )
            val movingBottoms = (220..320 step 5).map { height ->
                val surfaceTop = engine.surfaceTop(
                    transition.window.height,
                    height,
                    FloatingSpeechPlacement.BELOW_PET
                )
                val surfaceScreenTop = transition.window.y + surfaceTop
                assertEquals(
                    pet.y + pet.height + gap,
                    surfaceScreenTop + config.tailSlotHeightPx
                )
                surfaceScreenTop + height
            }

            assertTrue(movingBottoms.zipWithNext().all { (first, second) -> second >= first })
        }
    }

    @Test
    fun `transition reuses an existing larger canvas without moving the visible card`() {
        val pet = FloatingSpeechPetBounds(80, 430, 156, 187)
        val transition = engine.resolveTransition(
            input = input(pet, naturalHeight = 260),
            placement = FloatingSpeechPlacement.ABOVE_PET,
            visualHeight = 260,
            targetHeight = 220,
            existingCanvasHeight = 360
        )
        val stable = engine.resolveStable(input(pet, naturalHeight = 260))

        assertEquals(360, transition.window.height)
        assertEquals(stable.surfaceScreenY, transition.surfaceScreenY)
        assertEquals(stable.surface.height, transition.surface.height)
    }

    private fun input(
        pet: FloatingSpeechPetBounds,
        naturalHeight: Int,
        gap: Int = config.clearancePx
    ): FloatingCardGeometryInput {
        return FloatingCardGeometryInput(
            safeArea = safeArea,
            petBounds = pet,
            cardWidth = 320,
            naturalHeight = naturalHeight,
            minimumHeight = 192,
            config = config.copy(clearancePx = gap)
        )
    }
}
