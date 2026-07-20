package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

import com.chen.memorizewords.core.sprite.SpriteClipId
import com.chen.memorizewords.core.sprite.SpritePackManifestParser
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseFloatingPetPackContractTest {
    private val actionPolicy = ManifestFloatingPetActionPolicy()
    private val validator = FloatingPetPackContractValidator(actionPolicy)

    @Test
    fun `green pet release package satisfies the floating pet contract`() {
        val releasePackage = releaseFile("green_pet_v1.zip")
        ZipFile(releasePackage).use { archive ->
            val names = archive.entries().asSequence().map { it.name }.toSet()
            assertEquals(setOf("manifest.json", "sprite.webp"), names)

            val manifest = archive.getInputStream(archive.getEntry("manifest.json"))
                .bufferedReader()
                .use(SpritePackManifestParser()::parse)
            val spriteBytes = archive.getInputStream(archive.getEntry("sprite.webp")).use { it.readBytes() }
            val spriteHeader = readExtendedWebpHeader(spriteBytes)

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
            assertTrue(spriteHeader.hasAlpha, "The downloadable pet background must remain transparent")
            assertTrue(spriteBytes.size.toLong() in 1..MAX_SPRITE_BYTES)
        }

        val catalogItem = releaseFile("green_pet_catalog_item.json").readText(Charsets.UTF_8)
        assertEquals(releasePackage.length(), catalogItem.longField("packageSizeBytes"))
        assertEquals(sha256(releasePackage), catalogItem.stringField("packageSha256"))
        assertEquals("green_pet", catalogItem.stringField("packId"))
        assertEquals(1L, catalogItem.longField("packVersion"))
        assertEquals(1L, catalogItem.longField("manifestSchemaVersion"))
    }

    @Test
    fun `green pet upload metadata and preview match the release files`() {
        val releaseDir = releaseDirectory()
        val checksumEntries = releaseFile("SHA256SUMS.txt")
            .readLines(Charsets.UTF_8)
            .filter(String::isNotBlank)
            .associate { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                require(parts.size == 2) { "Invalid SHA256SUMS entry: $line" }
                parts[1] to parts[0]
            }
        val expectedFiles = setOf("green_pet_v1.zip", "green_pet_preview.png")
        assertEquals(expectedFiles, checksumEntries.keys)
        expectedFiles.forEach { fileName ->
            assertEquals(checksumEntries.getValue(fileName), sha256(File(releaseDir, fileName)))
        }

        val preview = readPngHeader(releaseFile("green_pet_preview.png").readBytes())
        assertEquals(112, preview.width)
        assertEquals(112, preview.height)
        assertTrue(preview.hasAlpha, "The upload preview must preserve transparency")

        val item = releaseFile("green_pet_catalog_item.json").readText(Charsets.UTF_8)
        val response = releaseFile("green_pet_catalog_response.example.json").readText(Charsets.UTF_8)
        listOf(
            "packId",
            "displayName",
            "description",
            "previewUrl",
            "packageUrl",
            "packageSha256"
        ).forEach { field ->
            assertEquals(item.stringField(field), response.stringField(field), field)
        }
        listOf(
            "packVersion",
            "sortOrder",
            "packageSizeBytes",
            "manifestSchemaVersion",
            "updatedAtMs"
        ).forEach { field ->
            assertEquals(item.longField(field), response.longField(field), field)
        }
    }

    @Test
    fun `green pet remains release only and is not bundled in Android assets`() {
        val bundledPackDirectory = File(
            featureModuleDirectory(),
            "src/main/assets/characters/green_pet"
        )
        assertFalse(
            bundledPackDirectory.walkTopDown().any(File::isFile),
            "Android main assets must not contain characters/green_pet"
        )

        val releaseDirectory = releaseDirectory()
        listOf(
            "green_pet_v1.zip",
            "green_pet_preview.png",
            "green_pet_catalog_item.json"
        ).forEach { name ->
            assertTrue(
                File(releaseDirectory, name).isFile,
                "Release file must remain available: $name"
            )
        }
    }

    private fun readExtendedWebpHeader(bytes: ByteArray): ExtendedWebpHeader {
        require(bytes.size >= VP8X_HEADER_BYTES) { "Truncated WebP header" }
        require(bytes.asAscii(0, 4) == "RIFF") { "Invalid WebP RIFF header" }
        require(bytes.asAscii(8, 4) == "WEBP") { "Invalid WebP signature" }
        require(bytes.asAscii(12, 4) == "VP8X") { "Expected an extended WebP header" }
        return ExtendedWebpHeader(
            width = bytes.readUInt24LittleEndian(24) + 1,
            height = bytes.readUInt24LittleEndian(27) + 1,
            hasAlpha = bytes[20].toInt() and VP8X_ALPHA_FLAG != 0
        )
    }

    private fun readPngHeader(bytes: ByteArray): PngHeader {
        require(bytes.size >= PNG_HEADER_BYTES) { "Truncated PNG header" }
        require(bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
            "Invalid PNG signature"
        }
        require(bytes.asAscii(12, 4) == "IHDR") { "Missing PNG IHDR chunk" }
        val colorType = bytes[25].toInt() and 0xFF
        return PngHeader(
            width = bytes.readIntBigEndian(16),
            height = bytes.readIntBigEndian(20),
            hasAlpha = colorType == PNG_COLOR_TYPE_GRAYSCALE_ALPHA ||
                colorType == PNG_COLOR_TYPE_TRUECOLOR_ALPHA
        )
    }

    private fun ByteArray.asAscii(offset: Int, length: Int): String =
        copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

    private fun ByteArray.readUInt24LittleEndian(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16)

    private fun ByteArray.readIntBigEndian(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private data class ExtendedWebpHeader(
        val width: Int,
        val height: Int,
        val hasAlpha: Boolean
    )

    private data class PngHeader(
        val width: Int,
        val height: Int,
        val hasAlpha: Boolean
    )

    private fun releaseDirectory(): File {
        return sequenceOf(File("../character-pack-release"), File("character-pack-release"))
            .firstOrNull(File::isDirectory)
            ?: error("Missing release directory: character-pack-release")
    }

    private fun featureModuleDirectory(): File {
        return sequenceOf(File("."), File("feature-floating-review"))
            .firstOrNull { directory -> File(directory, "src/main").isDirectory }
            ?: error("Missing feature module directory: feature-floating-review")
    }

    private fun releaseFile(name: String): File = File(releaseDirectory(), name).also { file ->
        require(file.isFile) { "Missing release file: ${file.path}" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun String.stringField(name: String): String {
        return Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?: error("Missing string field: $name")
    }

    private fun String.longField(name: String): Long {
        return Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*(\\d+)")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.toLong()
            ?: error("Missing numeric field: $name")
    }

    private companion object {
        const val MAX_SPRITE_BYTES = 4L * 1_024L * 1_024L
        const val VP8X_HEADER_BYTES = 30
        const val VP8X_ALPHA_FLAG = 0x10
        const val PNG_HEADER_BYTES = 29
        const val PNG_COLOR_TYPE_GRAYSCALE_ALPHA = 4
        const val PNG_COLOR_TYPE_TRUECOLOR_ALPHA = 6
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
        )
    }
}
