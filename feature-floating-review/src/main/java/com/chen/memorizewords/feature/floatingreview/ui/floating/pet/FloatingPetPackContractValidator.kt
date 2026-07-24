package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

import com.chen.memorizewords.core.sprite.DEFAULT_SPRITE_TEXTURE_ID
import com.chen.memorizewords.core.sprite.SpriteClipId
import com.chen.memorizewords.core.sprite.SpritePackContractValidator
import com.chen.memorizewords.core.sprite.SpritePackManifest
import com.chen.memorizewords.core.sprite.SpritePlaybackMode
import javax.inject.Inject

class FloatingPetPackContractValidator @Inject constructor(
    private val actionPolicy: FloatingPetActionPolicy
) : SpritePackContractValidator {
    override fun validate(manifest: SpritePackManifest) {
        require(manifest.schemaVersion == SpritePackManifest.KTX2_SCHEMA_VERSION) {
            "Only KTX2 schema-2 floating-pet packages are supported"
        }
        val decodedFrameBytes = manifest.atlas.frameWidth.toLong() *
            manifest.atlas.frameHeight * ARGB_8888_BYTES_PER_PIXEL
        require(decodedFrameBytes <= MAX_FLOATING_PET_FRAME_BYTES) {
            "Floating pet frame exceeds the supported replacement memory slot"
        }
        if (manifest.schemaVersion == SpritePackManifest.KTX2_SCHEMA_VERSION) {
            require(manifest.clips.values.all { it.framesPerSecond == KTX2_TIMELINE_FPS }) {
                "Schema-v2 floating pet clips must use the fixed 30 FPS timeline"
            }
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
            SpritePlaybackMode.HOLD_LAST,
            requireReversible = true
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
            SpritePlaybackMode.HOLD_LAST,
            requireReversible = true
        )
        val standardActionKeys = StandardPetAction.values()
            .mapTo(mutableSetOf(), StandardPetAction::semanticKey)
        manifest.semanticBindings.forEach { (semanticKey, clipId) ->
            if (semanticKey !in standardActionKeys) {
                requireFiniteAction(manifest, semanticKey, clipId)
            }
        }
        manifest.eventBindings.forEach { (eventKey, clipId) ->
            requireFiniteAction(manifest, eventKey, clipId)
        }
    }

    private fun requireMode(
        manifest: SpritePackManifest,
        action: StandardPetAction,
        vararg allowedModes: SpritePlaybackMode,
        requireReversible: Boolean = false
    ) {
        require(action.semanticKey in manifest.semanticBindings) {
            "Missing required floating-pet semantic ${action.semanticKey}"
        }
        val clipId = actionPolicy.resolveStandardAction(manifest, action)
        val clip = manifest.clip(clipId)
        require(clip.playbackMode in allowedModes) {
            "${action.semanticKey} must use one of ${allowedModes.toList()}, but was ${clip.playbackMode}"
        }
        if (manifest.schemaVersion == SpritePackManifest.KTX2_SCHEMA_VERSION) {
            require(clip.textureId == DEFAULT_SPRITE_TEXTURE_ID) {
                "${action.semanticKey} must use the resident standard texture"
            }
        }
        if (requireReversible) {
            require(clip.reversible) {
                "${action.semanticKey} must support immediate reverse playback"
            }
        }
    }

    private fun requireFiniteAction(
        manifest: SpritePackManifest,
        actionKey: String,
        clipId: SpriteClipId
    ) {
        require(manifest.clip(clipId).playbackMode != SpritePlaybackMode.LOOP) {
            "Optional action $actionKey must be finite until an explicit stop command exists"
        }
    }

    companion object {
        const val MAX_FLOATING_PET_FRAME_BYTES = 336L * 336L * 4L
        private const val KTX2_TIMELINE_FPS = 30
        private const val ARGB_8888_BYTES_PER_PIXEL = 4L
    }
}
