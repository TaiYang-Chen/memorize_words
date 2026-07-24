package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

import com.chen.memorizewords.core.sprite.SpriteAtlasSource
import com.chen.memorizewords.core.sprite.SpriteAtlasSpec
import com.chen.memorizewords.core.sprite.SpriteClipId
import com.chen.memorizewords.core.sprite.SpriteClipSpec
import com.chen.memorizewords.core.sprite.SpritePack
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.sprite.SpritePackManifest
import com.chen.memorizewords.core.sprite.SpritePackRuntimeRole
import com.chen.memorizewords.core.sprite.SpritePlaybackMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FloatingPetRuntimeFallbackPolicyTest {
    @Test
    fun primaryRevisionCompletesRequestedReload() {
        val primary = pack(SpritePackRuntimeRole.PRIMARY)

        assertEquals(primary.manifest.packId, runtimePackLoadCompletionId(primary))
    }

    @Test
    fun lastKnownGoodRevisionNeverCompletesRequestedReload() {
        assertNull(
            runtimePackLoadCompletionId(
                pack(SpritePackRuntimeRole.LAST_KNOWN_GOOD)
            )
        )
    }

    private fun pack(role: SpritePackRuntimeRole): SpritePack {
        val packId = SpritePackId("green_pet")
        val idle = SpriteClipId("idle")
        return SpritePack(
            manifest = SpritePackManifest(
                schemaVersion = 1,
                packId = packId,
                packVersion = 1,
                atlas = SpriteAtlasSpec("sprite.webp", 1, 1, 1, 1, 1, 1),
                clips = mapOf(
                    idle to SpriteClipSpec(
                        id = idle,
                        startFrame = 0,
                        frameCount = 1,
                        framesPerSecond = 30,
                        playbackMode = SpritePlaybackMode.HOLD_LAST
                    )
                ),
                semanticBindings = mapOf("idle" to idle),
                fallbackClipId = idle
            ),
            atlasSource = SpriteAtlasSource.LocalFile(File("sprite.webp")),
            runtimeRole = role
        )
    }
}
