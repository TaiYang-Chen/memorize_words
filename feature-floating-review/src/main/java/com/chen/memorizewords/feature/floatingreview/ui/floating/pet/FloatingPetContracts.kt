package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

import com.chen.memorizewords.core.sprite.SpriteClipId
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.sprite.SpritePackManifest
import kotlinx.coroutines.CompletableDeferred

enum class StandardPetAction(val semanticKey: String) {
    IDLE("idle"),
    CARD_OPEN("card_open"),
    CARD_VISIBLE("card_visible"),
    CARD_CLOSE("card_close")
}

sealed interface FloatingPetCommand {
    data class SetCardVisible(val visible: Boolean) : FloatingPetCommand
    data class PlayOptionalAction(val actionId: String) : FloatingPetCommand
    data class SwitchPack(
        val packId: SpritePackId,
        val forceReload: Boolean = false,
        val completion: CompletableDeferred<SpritePackId?>? = null
    ) : FloatingPetCommand
    data class Detach(val attachmentGeneration: Long) : FloatingPetCommand
    data object Release : FloatingPetCommand
}

interface FloatingPetActionPolicy {
    fun resolveStandardAction(
        manifest: SpritePackManifest,
        action: StandardPetAction
    ): SpriteClipId

    fun resolveOptionalAction(
        manifest: SpritePackManifest,
        actionId: String
    ): SpriteClipId?
}

class ManifestFloatingPetActionPolicy : FloatingPetActionPolicy {
    override fun resolveStandardAction(
        manifest: SpritePackManifest,
        action: StandardPetAction
    ): SpriteClipId = manifest.semanticBindings[action.semanticKey] ?: manifest.fallbackClipId

    override fun resolveOptionalAction(
        manifest: SpritePackManifest,
        actionId: String
    ): SpriteClipId? = if (actionId in STANDARD_ACTION_KEYS) {
        null
    } else {
        manifest.semanticBindings[actionId]
    }

    private companion object {
        val STANDARD_ACTION_KEYS = StandardPetAction.values()
            .mapTo(mutableSetOf(), StandardPetAction::semanticKey)
    }
}

enum class FloatingPetPlaybackState {
    UNINITIALIZED,
    IDLE,
    OPENING,
    VISIBLE_LOOP,
    CLOSING,
    OPTIONAL,
    SWITCHING_PACK,
    RELEASED
}
