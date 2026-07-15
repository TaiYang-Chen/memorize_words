package com.chen.memorizewords.core.sprite

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpritePackManifestParserTest {
    @Test
    fun `parses versioned manifest and continuous clip ranges`() {
        val manifest = SpritePackManifestParser().parse(StringReader(validManifest))

        assertEquals(SpritePackId("green_pet"), manifest.packId)
        assertEquals(73, manifest.atlas.frameCount)
        assertEquals(0 until 25, manifest.clips.getValue(SpriteClipId("card_open")).let {
            it.startFrame until it.endFrameExclusive
        })
        assertEquals(
            SpriteClipId("card_visible"),
            manifest.semanticBindings.getValue("card_visible")
        )
    }

    @Test
    fun `rejects clips that exceed the atlas`() {
        val invalid = validManifest.replace("\"frameCount\": 73", "\"frameCount\": 72")

        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(invalid))
        }
    }

    @Test
    fun `rejects atlas columns greater than the frame count`() {
        val invalid = validManifest.replace("\"columns\": 9", "\"columns\": 74")

        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(invalid))
        }
    }

    @Test
    fun `rejects zero columns with a validation error`() {
        val invalid = validManifest.replace("\"columns\": 9", "\"columns\": 0")

        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(invalid))
        }
    }

    @Test
    fun `rejects zero fps with a validation error`() {
        val invalid = validManifest.replace("\"fps\": 24", "\"fps\": 0")

        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(invalid))
        }
    }

    @Test
    fun `rejects overflowing clip end frame`() {
        val invalid = validManifest.replace(
            "\"card_open\": {\"startFrame\": 0, \"frameCount\": 25",
            "\"card_open\": {\"startFrame\": 2147483647, \"frameCount\": 25"
        )

        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(invalid))
        }
    }

    @Test
    fun `reverse frames preserve every source frame`() {
        val clip = SpritePackManifestParser().parse(StringReader(validManifest))
            .clips.getValue(SpriteClipId("card_close"))

        assertTrue(
            clip.frameIndices().asList().zipWithNext().all { (first, second) ->
                second - first == 1
            }
        )
        assertTrue(
            clip.frameIndices(reverse = true).asList().zipWithNext().all { (first, second) ->
                first - second == 1
            }
        )
    }

    @Test
    fun `rejects a loop that exceeds the decoded cache budget`() {
        val oversizedLoop = validManifest.replace(
            "\"card_visible\": {\"startFrame\": 25, \"frameCount\": 24",
            "\"card_visible\": {\"startFrame\": 25, \"frameCount\": 30"
        )

        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(oversizedLoop))
        }
    }

    @Test
    fun `rejects a loop that leaves no transition memory budget`() {
        val idle = SpriteClipId("idle")
        val loop = SpriteClipId("loop")
        val manifest = SpritePackManifest(
            schemaVersion = 1,
            packId = SpritePackId("large_pet"),
            packVersion = 1,
            atlas = SpriteAtlasSpec(
                fileName = "sprite.webp",
                width = 2_048,
                height = 2_048,
                frameWidth = 1_024,
                frameHeight = 1_024,
                columns = 2,
                frameCount = 4
            ),
            clips = mapOf(
                idle to SpriteClipSpec(idle, 0, 1, 24, SpritePlaybackMode.HOLD_LAST),
                loop to SpriteClipSpec(loop, 1, 3, 24, SpritePlaybackMode.LOOP)
            ),
            semanticBindings = mapOf("idle" to idle, "loop" to loop),
            fallbackClipId = idle
        )

        assertFailsWith<SpritePackValidationException> {
            SpritePackValidator().validate(manifest)
        }
    }

    @Test
    fun `rejects a clip map whose key differs from the clip id`() {
        val idleKey = SpriteClipId("idle")
        val mismatchedClip = SpriteClipSpec(
            id = SpriteClipId("other"),
            startFrame = 0,
            frameCount = 1,
            framesPerSecond = 24,
            playbackMode = SpritePlaybackMode.HOLD_LAST
        )
        val manifest = SpritePackManifest(
            schemaVersion = 1,
            packId = SpritePackId("mismatched_pet"),
            packVersion = 1,
            atlas = SpriteAtlasSpec("sprite.webp", 1, 1, 1, 1, 1, 1),
            clips = mapOf(idleKey to mismatchedClip),
            semanticBindings = mapOf("idle" to idleKey),
            fallbackClipId = idleKey
        )

        assertFailsWith<SpritePackValidationException> {
            SpritePackValidator().validate(manifest)
        }
    }

    private val validManifest = """
        {
          "schemaVersion": 1,
          "packId": "green_pet",
          "packVersion": 1,
          "fallbackClip": "idle",
          "atlas": {
            "file": "sprite.webp",
            "width": 3024,
            "height": 3024,
            "frameWidth": 336,
            "frameHeight": 336,
            "columns": 9,
            "frameCount": 73
          },
          "clips": {
            "idle": {"startFrame": 0, "frameCount": 1, "fps": 24, "mode": "hold_last"},
            "card_open": {"startFrame": 0, "frameCount": 25, "fps": 24, "mode": "once", "next": "card_visible", "reversible": true},
            "card_visible": {"startFrame": 25, "frameCount": 24, "fps": 24, "mode": "loop"},
            "card_close": {"startFrame": 49, "frameCount": 24, "fps": 24, "mode": "once", "next": "idle"}
          },
          "semanticBindings": {
            "idle": "idle",
            "card_open": "card_open",
            "card_visible": "card_visible",
            "card_close": "card_close"
          }
        }
    """.trimIndent()
}
