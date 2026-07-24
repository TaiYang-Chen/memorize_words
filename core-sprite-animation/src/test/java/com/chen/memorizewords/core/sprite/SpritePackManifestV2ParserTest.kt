package com.chen.memorizewords.core.sprite

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SpritePackManifestV2ParserTest {
    @Test
    fun `parses renderer-neutral KTX2 timeline and event bindings`() {
        val manifest = SpritePackManifestParser().parse(StringReader(validV2Manifest))

        assertEquals(2, manifest.schemaVersion)
        assertEquals(SpriteClipId("tap"), manifest.eventBindings.getValue("pet_tap"))
        assertEquals(
            SpriteTextureId("standard"),
            manifest.clips.getValue(SpriteClipId("card_close")).textureId
        )
        val texture = assertIs<Ktx2PagedTextureSpec>(
            manifest.textures.getValue(SpriteTextureId("standard"))
        )
        assertEquals(25, texture.framesPerPage)
        assertEquals(5, texture.requiredPageCount)
        assertEquals(14_112_000L, texture.estimatedGpuResidentBytes)
        assertEquals("standard.ktx2", manifest.atlas.fileName)
    }

    @Test
    fun `rejects schema v1 WebP manifests`() {
        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(validV1Manifest))
        }
    }

    @Test
    fun `rejects a clip that references a missing texture`() {
        val invalid = validV2Manifest.replace(
            "\"texture\": \"standard\", \"startFrame\": 61",
            "\"texture\": \"missing\", \"startFrame\": 61"
        )

        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(invalid))
        }
    }

    @Test
    fun `rejects a page count that does not match the grid`() {
        val invalid = validV2Manifest.replace("\"pageCount\": 5", "\"pageCount\": 4")

        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(invalid))
        }
    }

    @Test
    fun `rejects a texture over the resident GPU budget`() {
        val invalid = validV2Manifest
            .replace("\"pageWidth\": 1680", "\"pageWidth\": 2048")
            .replace("\"pageHeight\": 1680", "\"pageHeight\": 2048")

        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(invalid))
        }
    }

    @Test
    fun `rejects unsafe or dangling event bindings`() {
        val invalidKey = validV2Manifest.replace("\"pet_tap\": \"tap\"", "\"Pet Tap\": \"tap\"")
        val missingClip = validV2Manifest.replace("\"pet_tap\": \"tap\"", "\"pet_tap\": \"missing\"")

        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(invalidKey))
        }
        assertFailsWith<SpritePackValidationException> {
            SpritePackManifestParser().parse(StringReader(missingClip))
        }
    }

    private val validV2Manifest = """
        {
          "schemaVersion": 2,
          "packId": "green_pet",
          "packVersion": 2,
          "fallbackClip": "idle",
          "textures": {
            "standard": {
              "type": "ktx2_paged",
              "file": "standard.ktx2",
              "frameWidth": 336,
              "frameHeight": 336,
              "pageWidth": 1680,
              "pageHeight": 1680,
              "columns": 5,
              "rows": 5,
              "pageCount": 5,
              "frameCount": 117,
              "basisMode": "uastc",
              "colorSpace": "srgb",
              "alphaMode": "straight"
            }
          },
          "clips": {
            "idle": {"texture": "standard", "startFrame": 0, "frameCount": 1, "fps": 30, "mode": "hold_last"},
            "card_open": {"texture": "standard", "startFrame": 0, "frameCount": 31, "fps": 30, "mode": "once", "next": "card_visible", "reversible": true},
            "card_visible": {"texture": "standard", "startFrame": 31, "frameCount": 30, "fps": 30, "mode": "loop"},
            "card_close": {"texture": "standard", "startFrame": 61, "frameCount": 56, "fps": 30, "mode": "once", "next": "idle", "reversible": true},
            "tap": {"texture": "standard", "startFrame": 31, "frameCount": 10, "fps": 30, "mode": "once"}
          },
          "semanticBindings": {
            "idle": "idle",
            "card_open": "card_open",
            "card_visible": "card_visible",
            "card_close": "card_close"
          },
          "eventBindings": {
            "pet_tap": "tap"
          }
        }
    """.trimIndent()

    private val validV1Manifest = """
        {
          "schemaVersion": 1,
          "packId": "green_pet",
          "packVersion": 1,
          "fallbackClip": "idle",
          "atlas": {
            "file": "sprite.webp",
            "width": 336,
            "height": 336,
            "frameWidth": 336,
            "frameHeight": 336,
            "columns": 1,
            "frameCount": 1
          },
          "clips": {
            "idle": {"startFrame": 0, "frameCount": 1, "fps": 24, "mode": "hold_last"}
          },
          "semanticBindings": {"idle": "idle"}
        }
    """.trimIndent()
}
