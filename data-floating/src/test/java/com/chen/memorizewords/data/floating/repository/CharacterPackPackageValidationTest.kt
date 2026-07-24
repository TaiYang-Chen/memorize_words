package com.chen.memorizewords.data.floating.repository

import com.chen.memorizewords.core.sprite.Ktx2HeaderParser
import com.chen.memorizewords.core.sprite.Ktx2PagedTextureSpec
import com.chen.memorizewords.core.sprite.SpritePackManifestParser
import com.chen.memorizewords.core.sprite.SpriteTextureId
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CharacterPackPackageValidationTest {
    @Test
    fun `schema two archive contains exactly the manifest referenced textures`() {
        val manifest = V2_MANIFEST.reader().use(SpritePackManifestParser()::parse)

        assertEquals(
            setOf("manifest.json", "standard.ktx2", "tap.ktx2"),
            CharacterPackPackageLayout.expectedEntries(manifest)
        )
        assertEquals(
            mapOf(
                SpriteTextureId("standard") to "standard.ktx2",
                SpriteTextureId("tap") to "tap.ktx2"
            ),
            CharacterPackPackageLayout.referencedTextureFiles(manifest)
        )
    }

    @Test
    fun `schema one archive is rejected`() {
        assertFailsWith<Exception> {
            V1_MANIFEST.reader().use(SpritePackManifestParser()::parse)
        }
    }

    @Test
    fun `matching KTX2 header and level index pass structural validation`() {
        val file = Files.createTempFile("character-pack", ".ktx2").toFile()
        try {
            file.writeBytes(validKtx2Bytes())
            CharacterPackKtx2ContainerValidator.validate(
                file = file,
                spec = standardTextureSpec(),
                maxBytes = 1_024L
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `KTX2 identifier and manifest geometry mismatches are rejected`() {
        val invalidIdentifier = Files.createTempFile("character-pack-id", ".ktx2").toFile()
        val validContainer = Files.createTempFile("character-pack-size", ".ktx2").toFile()
        try {
            val invalidBytes = validKtx2Bytes().apply { this[0] = 0 }
            invalidIdentifier.writeBytes(invalidBytes)
            validContainer.writeBytes(validKtx2Bytes())

            assertFailsWith<IllegalArgumentException> {
                CharacterPackKtx2ContainerValidator.validate(
                    file = invalidIdentifier,
                    spec = standardTextureSpec(),
                    maxBytes = 1_024L
                )
            }
            assertFailsWith<IllegalArgumentException> {
                CharacterPackKtx2ContainerValidator.validate(
                    file = validContainer,
                    spec = standardTextureSpec().copy(pageWidth = 8),
                    maxBytes = 1_024L
                )
            }
        } finally {
            invalidIdentifier.delete()
            validContainer.delete()
        }
    }

    private fun standardTextureSpec(): Ktx2PagedTextureSpec {
        val manifest = V2_MANIFEST.reader().use(SpritePackManifestParser()::parse)
        return manifest.textures.getValue(SpriteTextureId("standard")) as Ktx2PagedTextureSpec
    }

    private fun validKtx2Bytes(): ByteArray {
        val dfdOffset = Ktx2HeaderParser.FIXED_HEADER_BYTES +
            Ktx2HeaderParser.LEVEL_INDEX_ENTRY_BYTES
        val levelOffset = dfdOffset + 4
        return ByteBuffer.allocate(levelOffset + 16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put(Ktx2HeaderParser.IDENTIFIER)
                putInt(0)
                putInt(1)
                putInt(4)
                putInt(4)
                putInt(0)
                putInt(1)
                putInt(1)
                putInt(1)
                putInt(2)
                putInt(dfdOffset)
                putInt(4)
                putInt(0)
                putInt(0)
                putLong(0L)
                putLong(0L)
                putLong(levelOffset.toLong())
                putLong(16L)
                putLong(16L)
                putInt(0)
                put(ByteArray(16))
            }
            .array()
    }

    private companion object {
        val V1_MANIFEST = """
            {
              "schemaVersion": 1,
              "packId": "green_pet",
              "packVersion": 1,
              "fallbackClip": "idle",
              "atlas": {
                "file": "sprite.webp",
                "width": 4,
                "height": 4,
                "frameWidth": 4,
                "frameHeight": 4,
                "columns": 1,
                "frameCount": 1
              },
              "clips": {
                "idle": {
                  "startFrame": 0,
                  "frameCount": 1,
                  "fps": 30,
                  "mode": "hold_last"
                }
              },
              "semanticBindings": {
                "idle": "idle"
              }
            }
        """.trimIndent()

        val V2_MANIFEST = """
            {
              "schemaVersion": 2,
              "packId": "green_pet",
              "packVersion": 2,
              "fallbackClip": "idle",
              "textures": {
                "standard": {
                  "type": "ktx2_paged",
                  "file": "standard.ktx2",
                  "frameWidth": 4,
                  "frameHeight": 4,
                  "pageWidth": 4,
                  "pageHeight": 4,
                  "columns": 1,
                  "rows": 1,
                  "pageCount": 1,
                  "frameCount": 1,
                  "basisMode": "uastc",
                  "colorSpace": "srgb",
                  "alphaMode": "straight"
                },
                "tap": {
                  "type": "ktx2_paged",
                  "file": "tap.ktx2",
                  "frameWidth": 4,
                  "frameHeight": 4,
                  "pageWidth": 4,
                  "pageHeight": 4,
                  "columns": 1,
                  "rows": 1,
                  "pageCount": 1,
                  "frameCount": 1,
                  "basisMode": "uastc",
                  "colorSpace": "srgb",
                  "alphaMode": "straight"
                }
              },
              "clips": {
                "idle": {
                  "texture": "standard",
                  "startFrame": 0,
                  "frameCount": 1,
                  "fps": 30,
                  "mode": "hold_last"
                },
                "tap": {
                  "texture": "tap",
                  "startFrame": 0,
                  "frameCount": 1,
                  "fps": 30,
                  "mode": "once",
                  "next": "idle"
                }
              },
              "semanticBindings": {
                "idle": "idle"
              },
              "eventBindings": {
                "pet_tap": "tap"
              }
            }
        """.trimIndent()
    }
}
