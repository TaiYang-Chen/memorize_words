package com.chen.memorizewords.core.sprite

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Ktx2HeaderParserTest {
    @Test
    fun `parses a single-level layered UASTC container`() {
        val header = Ktx2HeaderParser().parse(validContainer())

        assertEquals(1680, header.pixelWidth)
        assertEquals(5, header.effectiveLayerCount)
        assertEquals(Ktx2SupercompressionScheme.ZSTD, header.supercompressionScheme)
        assertEquals(14_112_000L, header.levels.single().uncompressedByteLength)
    }

    @Test
    fun `validates the KTX2 header against paged texture geometry`() {
        val header = Ktx2HeaderParser().parse(validContainer())

        Ktx2PagedTextureHeaderValidator().validate(textureSpec(), header)
    }

    @Test
    fun `rejects an invalid identifier`() {
        val bytes = validContainer().also { it[0] = 0 }

        assertFailsWith<Ktx2FormatException> { Ktx2HeaderParser().parse(bytes) }
    }

    @Test
    fun `rejects a level range outside the container`() {
        val bytes = validContainer().also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putLong(80, 180L)
        }

        assertFailsWith<Ktx2FormatException> { Ktx2HeaderParser().parse(bytes) }
    }

    @Test
    fun `rejects a layer count that disagrees with the manifest`() {
        val bytes = validContainer().also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(32, 4)
        }
        val header = Ktx2HeaderParser().parse(bytes)

        assertFailsWith<Ktx2FormatException> {
            Ktx2PagedTextureHeaderValidator().validate(textureSpec(), header)
        }
    }

    @Test
    fun `rejects an unexpected UASTC level byte count`() {
        val bytes = validContainer().also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putLong(96, 1L)
        }
        val header = Ktx2HeaderParser().parse(bytes)

        assertFailsWith<Ktx2FormatException> {
            Ktx2PagedTextureHeaderValidator().validate(textureSpec(), header)
        }
    }

    private fun textureSpec() = Ktx2PagedTextureSpec(
        id = SpriteTextureId("standard"),
        fileName = "standard.ktx2",
        frameWidth = 336,
        frameHeight = 336,
        pageWidth = 1680,
        pageHeight = 1680,
        columns = 5,
        rows = 5,
        pageCount = 5,
        frameCount = 117,
        basisMode = BasisCompressionMode.UASTC,
        colorSpace = SpriteColorSpace.SRGB,
        alphaMode = SpriteAlphaMode.STRAIGHT
    )

    private fun validContainer(): ByteArray {
        val bytes = ByteArray(192)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(Ktx2HeaderParser.IDENTIFIER)
        buffer.putInt(0) // vkFormat: undefined for Basis Universal
        buffer.putInt(1) // typeSize
        buffer.putInt(1680)
        buffer.putInt(1680)
        buffer.putInt(0) // pixelDepth
        buffer.putInt(5) // array layers/pages
        buffer.putInt(1) // faceCount
        buffer.putInt(1) // levelCount
        buffer.putInt(Ktx2SupercompressionScheme.ZSTD.code)
        buffer.putInt(104) // DFD offset, immediately after level index
        buffer.putInt(24)
        buffer.putInt(0) // no KVD
        buffer.putInt(0)
        buffer.putLong(0L) // no SGD
        buffer.putLong(0L)
        buffer.putLong(128L) // level byte offset
        buffer.putLong(64L) // compressed byte length for this fixture
        buffer.putLong(14_112_000L) // five 1680x1680 UASTC layers
        return bytes
    }
}
