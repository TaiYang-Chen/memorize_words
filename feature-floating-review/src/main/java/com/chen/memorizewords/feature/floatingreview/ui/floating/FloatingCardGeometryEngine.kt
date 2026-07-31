package com.chen.memorizewords.feature.floatingreview.ui.floating

internal data class FloatingCardGeometryInput(
    val safeArea: FloatingSpeechSafeArea,
    val petBounds: FloatingSpeechPetBounds,
    val cardWidth: Int,
    val naturalHeight: Int,
    val minimumHeight: Int,
    val config: FloatingSpeechLayoutConfig
)

internal data class FloatingCardWindowFrame(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

internal data class FloatingCardSurfaceFrame(
    val top: Int,
    val width: Int,
    val height: Int
)

internal data class FloatingCardLayoutSnapshot(
    val placement: FloatingSpeechPlacement,
    val window: FloatingCardWindowFrame,
    val surface: FloatingCardSurfaceFrame,
    val surfaceScreenY: Int,
    val tailCenterX: Int,
    val targetHeight: Int
)

internal class FloatingCardGeometryEngine(
    private val speechLayoutEngine: FloatingSpeechLayoutEngine = FloatingSpeechLayoutEngine()
) {
    fun resolvePlacement(input: FloatingCardGeometryInput): FloatingSpeechPlacement {
        return speechLayoutEngine.resolvePlacementForMinimumHeight(
            safeArea = input.safeArea,
            petBounds = input.petBounds,
            minimumCardHeightPx = input.minimumHeight,
            config = input.config
        )
    }

    fun resolveTargetHeight(
        input: FloatingCardGeometryInput,
        placement: FloatingSpeechPlacement
    ): Int {
        val availableHeight = speechLayoutEngine.availableHeight(
            safeArea = input.safeArea,
            petBounds = input.petBounds,
            config = input.config,
            placement = placement
        )
        return resolveFloatingCardTargetHeight(
            naturalHeightPx = input.naturalHeight,
            minimumHeightPx = input.minimumHeight,
            cardWidthPx = input.cardWidth,
            placementAvailableHeightPx = availableHeight
        )
    }

    fun resolveStable(
        input: FloatingCardGeometryInput,
        placement: FloatingSpeechPlacement = resolvePlacement(input)
    ): FloatingCardLayoutSnapshot {
        val targetHeight = resolveTargetHeight(input, placement)
        return resolve(
            input = input,
            placement = placement,
            visualHeight = targetHeight,
            canvasHeight = targetHeight,
            targetHeight = targetHeight
        )
    }

    fun resolveTransition(
        input: FloatingCardGeometryInput,
        placement: FloatingSpeechPlacement,
        visualHeight: Int,
        targetHeight: Int,
        existingCanvasHeight: Int = 0
    ): FloatingCardLayoutSnapshot {
        val canvasHeight = maxOf(
            visualHeight,
            targetHeight,
            existingCanvasHeight
        ).coerceAtLeast(1)
        return resolve(
            input = input,
            placement = placement,
            visualHeight = visualHeight.coerceIn(1, canvasHeight),
            canvasHeight = canvasHeight,
            targetHeight = targetHeight.coerceIn(1, canvasHeight)
        )
    }

    fun surfaceTop(
        canvasHeight: Int,
        visualHeight: Int,
        placement: FloatingSpeechPlacement
    ): Int {
        return when (placement) {
            FloatingSpeechPlacement.ABOVE_PET -> canvasHeight - visualHeight
            FloatingSpeechPlacement.BELOW_PET -> 0
        }
    }

    private fun resolve(
        input: FloatingCardGeometryInput,
        placement: FloatingSpeechPlacement,
        visualHeight: Int,
        canvasHeight: Int,
        targetHeight: Int
    ): FloatingCardLayoutSnapshot {
        val anchored = speechLayoutEngine.resolveAnchored(
            safeArea = input.safeArea,
            petBounds = input.petBounds,
            cardSize = FloatingSpeechCardSize(input.cardWidth, canvasHeight),
            config = input.config,
            placement = placement
        )
        val surfaceTop = surfaceTop(canvasHeight, visualHeight, placement)
        return FloatingCardLayoutSnapshot(
            placement = placement,
            window = FloatingCardWindowFrame(
                x = anchored.cardX,
                y = anchored.cardY,
                width = input.cardWidth,
                height = canvasHeight
            ),
            surface = FloatingCardSurfaceFrame(
                top = surfaceTop,
                width = input.cardWidth,
                height = visualHeight
            ),
            surfaceScreenY = anchored.cardY + surfaceTop,
            tailCenterX = anchored.tailCenterX,
            targetHeight = targetHeight
        )
    }
}
