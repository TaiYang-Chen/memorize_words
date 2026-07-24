package com.chen.memorizewords.core.sprite

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class Ktx2SupercompressionScheme(val code: Int) {
    NONE(0),
    BASIS_LZ(1),
    ZSTD(2),
    ZLIB(3);

    companion object {
        fun fromCode(code: Int): Ktx2SupercompressionScheme? =
            values().firstOrNull { it.code == code }
    }
}

data class Ktx2LevelIndex(
    val byteOffset: Long,
    val byteLength: Long,
    val uncompressedByteLength: Long
) {
    val endOffsetExclusive: Long
        get() = Math.addExact(byteOffset, byteLength)
}

data class Ktx2Header(
    val vkFormat: Long,
    val typeSize: Int,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val pixelDepth: Int,
    /** Zero denotes a non-array texture and therefore one effective layer. */
    val layerCount: Int,
    val faceCount: Int,
    val levelCount: Int,
    val supercompressionScheme: Ktx2SupercompressionScheme,
    val dfdByteOffset: Long,
    val dfdByteLength: Long,
    val kvdByteOffset: Long,
    val kvdByteLength: Long,
    val sgdByteOffset: Long,
    val sgdByteLength: Long,
    val levels: List<Ktx2LevelIndex>
) {
    val effectiveLayerCount: Int
        get() = layerCount.coerceAtLeast(1)
}

data class Ktx2HeaderConstraints(
    val maxDimension: Int = 16_384,
    val maxLayerCount: Int = 256,
    val maxLevelCount: Int = 32,
    val maxContainerBytes: Long = 64L * 1_024L * 1_024L
)

class Ktx2HeaderParser(
    private val constraints: Ktx2HeaderConstraints = Ktx2HeaderConstraints()
) {
    fun parse(bytes: ByteArray): Ktx2Header =
        ByteArrayInputStream(bytes).use { input -> parse(input, bytes.size.toLong()) }

    fun parse(input: InputStream, fileSize: Long? = null): Ktx2Header {
        if (fileSize != null && fileSize !in FIXED_HEADER_BYTES.toLong()..constraints.maxContainerBytes) {
            throw Ktx2FormatException("KTX2 container size is invalid")
        }
        val fixedHeader = input.readExactly(FIXED_HEADER_BYTES)
        if (!fixedHeader.copyOfRange(0, IDENTIFIER.size).contentEquals(IDENTIFIER)) {
            throw Ktx2FormatException("Invalid KTX2 identifier")
        }
        val buffer = ByteBuffer.wrap(fixedHeader).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(IDENTIFIER.size)
        val vkFormat = buffer.readUnsignedInt()
        val typeSize = buffer.readBoundedInt("typeSize", Int.MAX_VALUE)
        val pixelWidth = buffer.readBoundedInt("pixelWidth", constraints.maxDimension)
        val pixelHeight = buffer.readBoundedInt("pixelHeight", constraints.maxDimension)
        val pixelDepth = buffer.readBoundedInt("pixelDepth", constraints.maxDimension)
        val layerCount = buffer.readBoundedInt("layerCount", constraints.maxLayerCount)
        val faceCount = buffer.readBoundedInt("faceCount", 6)
        val levelCount = buffer.readBoundedInt("levelCount", constraints.maxLevelCount)
        if (levelCount <= 0) throw Ktx2FormatException("KTX2 must contain at least one level")
        val supercompressionCode = buffer.readBoundedInt("supercompressionScheme", Int.MAX_VALUE)
        val supercompression = Ktx2SupercompressionScheme.fromCode(supercompressionCode)
            ?: throw Ktx2FormatException("Unsupported KTX2 supercompression scheme")
        val dfdByteOffset = buffer.readUnsignedInt()
        val dfdByteLength = buffer.readUnsignedInt()
        val kvdByteOffset = buffer.readUnsignedInt()
        val kvdByteLength = buffer.readUnsignedInt()
        val sgdByteOffset = buffer.readNonNegativeLong("sgdByteOffset")
        val sgdByteLength = buffer.readNonNegativeLong("sgdByteLength")

        val levelIndexBytes = Math.multiplyExact(levelCount, LEVEL_INDEX_ENTRY_BYTES)
        val levelBuffer = ByteBuffer.wrap(input.readExactly(levelIndexBytes))
            .order(ByteOrder.LITTLE_ENDIAN)
        val levels = List(levelCount) {
            Ktx2LevelIndex(
                byteOffset = levelBuffer.readNonNegativeLong("level byteOffset"),
                byteLength = levelBuffer.readNonNegativeLong("level byteLength"),
                uncompressedByteLength = levelBuffer.readNonNegativeLong(
                    "level uncompressedByteLength"
                )
            )
        }
        val header = Ktx2Header(
            vkFormat = vkFormat,
            typeSize = typeSize,
            pixelWidth = pixelWidth,
            pixelHeight = pixelHeight,
            pixelDepth = pixelDepth,
            layerCount = layerCount,
            faceCount = faceCount,
            levelCount = levelCount,
            supercompressionScheme = supercompression,
            dfdByteOffset = dfdByteOffset,
            dfdByteLength = dfdByteLength,
            kvdByteOffset = kvdByteOffset,
            kvdByteLength = kvdByteLength,
            sgdByteOffset = sgdByteOffset,
            sgdByteLength = sgdByteLength,
            levels = levels
        )
        validateStructure(header, fileSize)
        return header
    }

    private fun validateStructure(header: Ktx2Header, fileSize: Long?) {
        if (header.pixelWidth <= 0) throw Ktx2FormatException("KTX2 pixel width is invalid")
        if (header.typeSize <= 0) throw Ktx2FormatException("KTX2 type size is invalid")
        if (header.faceCount != 1 && header.faceCount != 6) {
            throw Ktx2FormatException("KTX2 face count is invalid")
        }
        val indexEnd = FIXED_HEADER_BYTES.toLong() +
            header.levelCount.toLong() * LEVEL_INDEX_ENTRY_BYTES
        val ranges = ArrayList<NamedRange>()
        ranges += metadataRange("DFD", header.dfdByteOffset, header.dfdByteLength, indexEnd, fileSize)
        if (header.dfdByteOffset % 4L != 0L) {
            throw Ktx2FormatException("KTX2 DFD offset is not 4-byte aligned")
        }
        if (header.kvdByteLength > 0L) {
            ranges += metadataRange(
                "KVD",
                header.kvdByteOffset,
                header.kvdByteLength,
                indexEnd,
                fileSize
            )
            if (header.kvdByteOffset % 4L != 0L) {
                throw Ktx2FormatException("KTX2 KVD offset is not 4-byte aligned")
            }
        } else if (header.kvdByteOffset != 0L) {
            throw Ktx2FormatException("Empty KTX2 KVD must have a zero offset")
        }
        if (header.sgdByteLength > 0L) {
            ranges += metadataRange(
                "SGD",
                header.sgdByteOffset,
                header.sgdByteLength,
                indexEnd,
                fileSize
            )
            if (header.sgdByteOffset % 8L != 0L) {
                throw Ktx2FormatException("KTX2 SGD offset is not 8-byte aligned")
            }
        } else if (header.sgdByteOffset != 0L) {
            throw Ktx2FormatException("Empty KTX2 SGD must have a zero offset")
        }
        header.levels.forEachIndexed { index, level ->
            if (level.byteLength <= 0L || level.uncompressedByteLength <= 0L) {
                throw Ktx2FormatException("KTX2 level $index has an invalid length")
            }
            ranges += metadataRange(
                "level $index",
                level.byteOffset,
                level.byteLength,
                indexEnd,
                fileSize
            )
        }
        ranges.sortedBy(NamedRange::offset).zipWithNext().forEach { (first, second) ->
            if (first.endOffsetExclusive > second.offset) {
                throw Ktx2FormatException("KTX2 ${first.name} overlaps ${second.name}")
            }
        }
    }

    private fun metadataRange(
        name: String,
        offset: Long,
        length: Long,
        minimumOffset: Long,
        fileSize: Long?
    ): NamedRange {
        if (length <= 0L) throw Ktx2FormatException("KTX2 $name is empty")
        if (offset < minimumOffset) throw Ktx2FormatException("KTX2 $name overlaps its header")
        val endOffset = try {
            Math.addExact(offset, length)
        } catch (_: ArithmeticException) {
            throw Ktx2FormatException("KTX2 $name range overflows")
        }
        if (fileSize != null && endOffset > fileSize) {
            throw Ktx2FormatException("KTX2 $name exceeds the container")
        }
        return NamedRange(name, offset, endOffset)
    }

    private data class NamedRange(
        val name: String,
        val offset: Long,
        val endOffsetExclusive: Long
    )

    companion object {
        const val FIXED_HEADER_BYTES = 80
        const val LEVEL_INDEX_ENTRY_BYTES = 24
        val IDENTIFIER = byteArrayOf(
            0xAB.toByte(), 0x4B, 0x54, 0x58, 0x20, 0x32,
            0x30, 0xBB.toByte(), 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}

class Ktx2PagedTextureHeaderValidator {
    fun validate(spec: Ktx2PagedTextureSpec, header: Ktx2Header) {
        requireKtx2(header.vkFormat == 0L) { "Basis KTX2 must use an undefined vkFormat" }
        requireKtx2(header.typeSize == 1) { "Basis KTX2 typeSize must be one" }
        requireKtx2(header.pixelWidth == spec.pageWidth) { "KTX2 page width does not match" }
        requireKtx2(header.pixelHeight == spec.pageHeight) { "KTX2 page height does not match" }
        requireKtx2(header.pixelDepth == 0) { "Sprite KTX2 must be two-dimensional" }
        requireKtx2(header.effectiveLayerCount == spec.pageCount) {
            "KTX2 layer count does not match the manifest page count"
        }
        requireKtx2(header.faceCount == 1) { "Sprite KTX2 must not be a cubemap" }
        requireKtx2(header.levelCount == 1) { "Sprite KTX2 must not contain mipmaps" }
        requireKtx2(header.supercompressionScheme == Ktx2SupercompressionScheme.ZSTD) {
            "UASTC sprite KTX2 must use Zstd supercompression"
        }
        requireKtx2(
            header.levels.single().uncompressedByteLength == spec.estimatedGpuResidentBytes
        ) {
            "KTX2 UASTC level size does not match the manifest page geometry"
        }
    }

    private fun requireKtx2(value: Boolean, lazyMessage: () -> String) {
        if (!value) throw Ktx2FormatException(lazyMessage())
    }
}

class Ktx2FormatException(message: String) : IllegalArgumentException(message)

private fun InputStream.readExactly(byteCount: Int): ByteArray {
    val bytes = ByteArray(byteCount)
    var offset = 0
    while (offset < byteCount) {
        val read = read(bytes, offset, byteCount - offset)
        if (read < 0) throw Ktx2FormatException("Truncated KTX2 container")
        if (read == 0) continue
        offset += read
    }
    return bytes
}

private fun ByteBuffer.readUnsignedInt(): Long = int.toLong() and 0xFFFF_FFFFL

private fun ByteBuffer.readBoundedInt(name: String, maximum: Int): Int {
    val value = readUnsignedInt()
    if (value > maximum.toLong()) throw Ktx2FormatException("KTX2 $name is too large")
    return value.toInt()
}

private fun ByteBuffer.readNonNegativeLong(name: String): Long {
    val value = long
    if (value < 0L) throw Ktx2FormatException("KTX2 $name exceeds signed 64-bit range")
    return value
}
