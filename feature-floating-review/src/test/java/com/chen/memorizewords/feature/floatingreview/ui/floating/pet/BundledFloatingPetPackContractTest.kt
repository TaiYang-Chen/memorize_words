package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

import com.chen.memorizewords.core.sprite.SpriteClipId
import com.chen.memorizewords.core.sprite.SpritePackManifestParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledFloatingPetPackContractTest {
    private val actionPolicy = ManifestFloatingPetActionPolicy()
    private val validator = FloatingPetPackContractValidator(actionPolicy)

    @Test
    fun `green pet production assets satisfy the floating pet contract`() {
        val manifestFile = productionAsset("characters/green_pet/manifest.json")
        val spriteFile = productionAsset("characters/green_pet/sprite.webp")
        val manifest = manifestFile.bufferedReader().use(SpritePackManifestParser()::parse)
        val spriteHeader = readExtendedWebpHeader(spriteFile)

        validator.validate(manifest)
        assertEquals(24, manifest.clips.values.minOf { it.framesPerSecond })
        assertEquals(24, manifest.clips.values.maxOf { it.framesPerSecond })
        assertTrue(manifest.clips.getValue(SpriteClipId("card_close")).reversible)

        val standardClips = listOf("card_open", "card_visible", "card_close")
            .map { manifest.clips.getValue(SpriteClipId(it)) }
        standardClips.zipWithNext().forEach { (current, next) ->
            assertEquals(current.startFrame + current.frameCount, next.startFrame)
        }
        val actionFrames = standardClips.flatMap { it.frameIndices().asList() }
        assertEquals(actionFrames.size, actionFrames.distinct().size)
        assertTrue(actionFrames.all { it in 0 until manifest.atlas.frameCount })
        assertEquals(manifest.atlas.width, spriteHeader.width)
        assertEquals(manifest.atlas.height, spriteHeader.height)
        assertTrue(spriteHeader.hasAlpha, "The bundled pet background must remain transparent")
        assertTrue(spriteFile.length() in 1..MAX_BUNDLED_SPRITE_BYTES)
    }

    private fun readExtendedWebpHeader(file: File): ExtendedWebpHeader {
        val header = file.inputStream().use { input ->
            ByteArray(VP8X_HEADER_BYTES).also { bytes ->
                require(input.read(bytes) == bytes.size) { "Truncated WebP header" }
            }
        }
        require(header.asAscii(0, 4) == "RIFF") { "Invalid WebP RIFF header" }
        require(header.asAscii(8, 4) == "WEBP") { "Invalid WebP signature" }
        require(header.asAscii(12, 4) == "VP8X") { "Expected an extended WebP header" }
        return ExtendedWebpHeader(
            width = header.readUInt24LittleEndian(24) + 1,
            height = header.readUInt24LittleEndian(27) + 1,
            hasAlpha = header[20].toInt() and VP8X_ALPHA_FLAG != 0
        )
    }

    private fun ByteArray.asAscii(offset: Int, length: Int): String =
        copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

    private fun ByteArray.readUInt24LittleEndian(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16)

    private data class ExtendedWebpHeader(
        val width: Int,
        val height: Int,
        val hasAlpha: Boolean
    )

    private fun productionAsset(relativePath: String): File {
        return sequenceOf(
            File("src/main/assets/$relativePath"),
            File("feature-floating-review/src/main/assets/$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Missing production asset: $relativePath")
    }

    private companion object {
        const val MAX_BUNDLED_SPRITE_BYTES = 4L * 1_024L * 1_024L
        const val VP8X_HEADER_BYTES = 30
        const val VP8X_ALPHA_FLAG = 0x10
    }
}
