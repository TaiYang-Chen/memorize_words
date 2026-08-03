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

enum class PetEvent(val wireName: String) {
    PET_TAP("pet_tap"),
    CARD_OPENED("card_opened"),
    CARD_CLOSED("card_closed"),
    WORD_CHANGED("word_changed"),
    FAVORITE_ADDED("favorite_added"),
    FAVORITE_REMOVED("favorite_removed"),
    DRAG_STARTED("drag_started"),
    DRAG_ENDED("drag_ended");

    companion object {
        private val valuesByWireName = entries.associateBy(PetEvent::wireName)

        fun fromWireName(wireName: String): PetEvent? = valuesByWireName[wireName]
    }
}

sealed interface FloatingPetCommand {
    data class SetCardVisible(val visible: Boolean) : FloatingPetCommand
    data class PlayEvent(val event: PetEvent) : FloatingPetCommand
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

    fun resolveEventAction(
        manifest: SpritePackManifest,
        event: PetEvent
    ): SpriteClipId?
}

class ManifestFloatingPetActionPolicy : FloatingPetActionPolicy {
    override fun resolveStandardAction(
        manifest: SpritePackManifest,
        action: StandardPetAction
    ): SpriteClipId = manifest.semanticBindings[action.semanticKey] ?: manifest.fallbackClipId

    override fun resolveEventAction(
        manifest: SpritePackManifest,
        event: PetEvent
    ): SpriteClipId? = manifest.eventBindings[event.wireName]
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
