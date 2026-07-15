package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

import com.chen.memorizewords.core.sprite.SpriteAtlasSpec
import com.chen.memorizewords.core.sprite.SpriteClipId
import com.chen.memorizewords.core.sprite.SpriteClipSpec
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.sprite.SpritePackManifest
import com.chen.memorizewords.core.sprite.SpritePlaybackMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ManifestFloatingPetActionPolicyTest {
    private val idle = SpriteClipId("idle")
    private val visible = SpriteClipId("visible_loop")
    private val manifest = SpritePackManifest(
        schemaVersion = 1,
        packId = SpritePackId("green_pet"),
        packVersion = 1,
        atlas = SpriteAtlasSpec("sprite.webp", 2, 1, 1, 1, 2, 2),
        clips = mapOf(
            idle to SpriteClipSpec(idle, 0, 1, 24, SpritePlaybackMode.HOLD_LAST),
            visible to SpriteClipSpec(visible, 1, 1, 24, SpritePlaybackMode.LOOP)
        ),
        semanticBindings = mapOf(StandardPetAction.CARD_VISIBLE.semanticKey to visible),
        fallbackClipId = idle
    )

    @Test
    fun `uses manifest binding for standard action`() {
        assertEquals(
            visible,
            ManifestFloatingPetActionPolicy().resolveStandardAction(
                manifest,
                StandardPetAction.CARD_VISIBLE
            )
        )
    }

    @Test
    fun `falls back when character omits a standard action`() {
        assertEquals(
            idle,
            ManifestFloatingPetActionPolicy().resolveStandardAction(
                manifest,
                StandardPetAction.CARD_CLOSE
            )
        )
    }

    @Test
    fun `standard loop cannot be invoked through the optional action API`() {
        assertNull(
            ManifestFloatingPetActionPolicy().resolveOptionalAction(
                manifest,
                StandardPetAction.CARD_VISIBLE.semanticKey
            )
        )
    }

    @Test
    fun `contract rejects looping transition actions`() {
        val invalidManifest = manifest.copy(
            semanticBindings = manifest.semanticBindings +
                (StandardPetAction.CARD_OPEN.semanticKey to visible)
        )

        assertFailsWith<IllegalArgumentException> {
            FloatingPetPackContractValidator(ManifestFloatingPetActionPolicy())
                .validate(invalidManifest)
        }
    }

    @Test
    fun `contract rejects optional loops without a stop command`() {
        val invalidManifest = manifest.copy(
            semanticBindings = manifest.semanticBindings + ("celebrate" to visible)
        )

        assertFailsWith<IllegalArgumentException> {
            FloatingPetPackContractValidator(ManifestFloatingPetActionPolicy())
                .validate(invalidManifest)
        }
    }

    @Test
    fun `contract rejects frames larger than the atomic replacement slot`() {
        val invalidManifest = manifest.copy(
            atlas = SpriteAtlasSpec("sprite.webp", 1_024, 512, 512, 512, 2, 2)
        )

        assertFailsWith<IllegalArgumentException> {
            FloatingPetPackContractValidator(ManifestFloatingPetActionPolicy())
                .validate(invalidManifest)
        }
    }
}
