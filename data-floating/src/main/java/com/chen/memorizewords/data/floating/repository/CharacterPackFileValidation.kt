package com.chen.memorizewords.data.floating.repository

import com.chen.memorizewords.core.sprite.SpritePackManifest
import com.chen.memorizewords.core.sprite.SpritePackManifestParser
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal object CharacterPackManifestFileReader {
    fun parse(
        file: File,
        maxBytes: Long,
        parser: SpritePackManifestParser
    ): SpritePackManifest {
        require(file.isFile && file.length() in 1..maxBytes) {
            "Character manifest size is invalid"
        }
        val bytes = file.readBytes()
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        return text.reader().use(parser::parse)
    }
}

internal object CharacterPackWebpContainerValidator {
    private const val HEADER_BYTES = 12

    fun validate(file: File, maxBytes: Long) {
        val actualLength = file.length()
        require(file.isFile && actualLength in HEADER_BYTES.toLong()..maxBytes) {
            "Character atlas size is invalid"
        }
        val header = ByteArray(HEADER_BYTES)
        file.inputStream().use { input ->
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                require(read > 0) { "Truncated WebP header" }
                offset += read
            }
        }
        require(header.asAscii(0, 4) == "RIFF" && header.asAscii(8, 4) == "WEBP") {
            "Invalid WebP container"
        }
        val declaredLength = header.readUInt32LittleEndian(4) + 8L
        require(declaredLength == actualLength) { "Truncated or extended WebP container" }
    }

    private fun ByteArray.asAscii(offset: Int, length: Int): String =
        copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

    private fun ByteArray.readUInt32LittleEndian(offset: Int): Long =
        (this[offset].toLong() and 0xffL) or
            ((this[offset + 1].toLong() and 0xffL) shl 8) or
            ((this[offset + 2].toLong() and 0xffL) shl 16) or
            ((this[offset + 3].toLong() and 0xffL) shl 24)
}
