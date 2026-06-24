package com.chen.memorizewords.speech

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.chen.memorizewords.speech.api.SpeechAudioFormat
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechCacheStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val rootDir = File(context.cacheDir, "speech_cache").apply { mkdirs() }

    fun resolveCacheFile(
        cacheKey: String,
        format: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ): File {
        return File(rootDir, "${sanitize(cacheKey)}.${extension(format)}")
    }

    fun getCachedFile(
        cacheKey: String,
        format: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ): File? {
        val file = resolveCacheFile(cacheKey, format)
        return file.takeIf { it.exists() && it.isFile }
    }

    fun copyIntoCache(
        cacheKey: String,
        source: File,
        format: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ): File? {
        return runCatching {
            val target = resolveCacheFile(cacheKey, format)
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
            }
            target
        }.getOrNull()
    }

    fun createTempFile(
        prefix: String,
        format: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ): File {
        val dir = File(rootDir, "tmp").apply { mkdirs() }
        return File(dir, "${sanitize(prefix)}_${System.currentTimeMillis()}.${extension(format)}")
    }

    fun stableHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun sanitize(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun extension(format: SpeechAudioFormat): String {
        val encoding = format.encoding.lowercase(Locale.US)
        val mimeType = format.mimeType.lowercase(Locale.US)
        return when {
            encoding.contains("wav") || mimeType.contains("wav") -> "wav"
            encoding.contains("pcm") || mimeType.contains("pcm") -> "pcm"
            encoding.contains("aac") || mimeType.contains("aac") || mimeType.contains("mp4") -> "m4a"
            else -> "mp3"
        }
    }
}
