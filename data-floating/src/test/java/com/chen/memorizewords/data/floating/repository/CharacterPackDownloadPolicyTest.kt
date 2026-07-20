package com.chen.memorizewords.data.floating.repository

import com.chen.memorizewords.core.sprite.SpritePackManifestParser
import java.io.IOException
import java.net.ProtocolException
import java.nio.file.Files
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterPackDownloadPolicyTest {
    @Test
    fun `content length must match catalog when server declares it`() {
        assertTrue(CharacterPackDownloadPolicy.contentLengthMatchesCatalog(-1L, 1_000L))
        assertTrue(CharacterPackDownloadPolicy.contentLengthMatchesCatalog(1_000L, 1_000L))
        assertFalse(CharacterPackDownloadPolicy.contentLengthMatchesCatalog(999L, 1_000L))
        assertFalse(CharacterPackDownloadPolicy.contentLengthMatchesCatalog(1_001L, 1_000L))
    }

    @Test
    fun `only transient HTTP failures are retried`() {
        listOf(408, 425, 429, 500, 503, 599).forEach { status ->
            assertTrue(CharacterPackDownloadPolicy.isRetryableHttpStatus(status), status.toString())
        }
        listOf(400, 401, 403, 404, 409, 422, 600).forEach { status ->
            assertFalse(CharacterPackDownloadPolicy.isRetryableHttpStatus(status), status.toString())
        }
    }

    @Test
    fun `TLS identity and protocol failures are not retried`() {
        assertFalse(
            CharacterPackDownloadPolicy.isRetryableTransportFailure(
                SSLHandshakeException("bad certificate")
            )
        )
        assertFalse(
            CharacterPackDownloadPolicy.isRetryableTransportFailure(
                SSLPeerUnverifiedException("wrong host")
            )
        )
        assertFalse(
            CharacterPackDownloadPolicy.isRetryableTransportFailure(
                ProtocolException("invalid response")
            )
        )
        assertTrue(CharacterPackDownloadPolicy.isRetryableTransportFailure(IOException("reset")))
    }

    @Test
    fun `storage estimates remain bounded and include install headroom`() {
        val packageBytes = 1_421_233L
        assertTrue(CharacterPackStoragePolicy.requiredInstallBytes(packageBytes) > packageBytes)
        assertEquals(
            packageBytes + CharacterPackStoragePolicy.requiredInstallBytes(packageBytes),
            CharacterPackStoragePolicy.requiredPeakBytes(packageBytes)
        )
        assertTrue(CharacterPackStoragePolicy.requiredSpaceMiB(packageBytes) >= 1L)
        assertEquals(Long.MAX_VALUE, CharacterPackStoragePolicy.requiredPeakBytes(Long.MAX_VALUE))
    }

    @Test
    fun `WebP container validation rejects a truncated length declaration`() {
        val valid = Files.createTempFile("character-pack-valid", ".webp").toFile()
        val invalid = Files.createTempFile("character-pack-invalid", ".webp").toFile()
        try {
            valid.writeBytes(webpHeader(declaredRiffBytes = 4L))
            invalid.writeBytes(webpHeader(declaredRiffBytes = 5L))

            CharacterPackWebpContainerValidator.validate(valid, 1_024L)
            assertFailsWith<IllegalArgumentException> {
                CharacterPackWebpContainerValidator.validate(invalid, 1_024L)
            }
        } finally {
            valid.delete()
            invalid.delete()
        }
    }

    @Test
    fun `manifest reader rejects malformed UTF-8`() {
        val manifest = Files.createTempFile("character-pack-manifest", ".json").toFile()
        try {
            manifest.writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
            assertFailsWith<Exception> {
                CharacterPackManifestFileReader.parse(
                    file = manifest,
                    maxBytes = 1_024L,
                    parser = SpritePackManifestParser()
                )
            }
        } finally {
            manifest.delete()
        }
    }

    private fun webpHeader(declaredRiffBytes: Long): ByteArray = ByteArray(12).apply {
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(this, 0)
        this[4] = (declaredRiffBytes and 0xffL).toByte()
        this[5] = ((declaredRiffBytes ushr 8) and 0xffL).toByte()
        this[6] = ((declaredRiffBytes ushr 16) and 0xffL).toByte()
        this[7] = ((declaredRiffBytes ushr 24) and 0xffL).toByte()
        "WEBP".toByteArray(Charsets.US_ASCII).copyInto(this, 8)
    }
}
