package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

import com.chen.memorizewords.core.sprite.SpritePackManifest
import com.chen.memorizewords.core.sprite.SpritePackContractValidator
import com.chen.memorizewords.core.sprite.SpritePlaybackMode
import javax.inject.Inject

class FloatingPetPackContractValidator @Inject constructor(
    private val actionPolicy: FloatingPetActionPolicy
) : SpritePackContractValidator {
    override fun validate(manifest: SpritePackManifest) {
        val decodedFrameBytes = manifest.atlas.frameWidth.toLong() *
            manifest.atlas.frameHeight * ARGB_8888_BYTES_PER_PIXEL
        require(decodedFrameBytes <= MAX_FLOATING_PET_FRAME_BYTES) {
            "Floating pet frame exceeds the supported replacement memory slot"
        }
        requireMode(
            manifest,
            StandardPetAction.IDLE,
            SpritePlaybackMode.ONCE,
            SpritePlaybackMode.HOLD_LAST
        )
        requireMode(
            manifest,
            StandardPetAction.CARD_OPEN,
            SpritePlaybackMode.ONCE,
            SpritePlaybackMode.HOLD_LAST
        )
        requireMode(
            manifest,
            StandardPetAction.CARD_VISIBLE,
            SpritePlaybackMode.LOOP
        )
        requireMode(
            manifest,
            StandardPetAction.CARD_CLOSE,
            SpritePlaybackMode.ONCE,
            SpritePlaybackMode.HOLD_LAST
        )
        val standardActionKeys = StandardPetAction.values()
            .mapTo(mutableSetOf(), StandardPetAction::semanticKey)
        manifest.semanticBindings.forEach { (semanticKey, clipId) ->
            if (semanticKey !in standardActionKeys) {
                require(manifest.clip(clipId).playbackMode != SpritePlaybackMode.LOOP) {
                    "Optional action $semanticKey must be finite until an explicit stop command exists"
                }
            }
        }
    }

    private fun requireMode(
        manifest: SpritePackManifest,
        action: StandardPetAction,
        vararg allowedModes: SpritePlaybackMode
    ) {
        val clipId = actionPolicy.resolveStandardAction(manifest, action)
        val actualMode = manifest.clip(clipId).playbackMode
        require(actualMode in allowedModes) {
            "${action.semanticKey} must use one of ${allowedModes.toList()}, but was $actualMode"
        }
    }

    companion object {
        const val MAX_FLOATING_PET_FRAME_BYTES = 336L * 336L * 4L
        private const val ARGB_8888_BYTES_PER_PIXEL = 4L
    }
}
