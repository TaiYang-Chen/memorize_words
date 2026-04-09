package com.chen.memorizewords.speech

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechCacheStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val rootDir = File(context.cacheDir, "speech_cache").apply { mkdirs() }

    fun resolveCacheFile(cacheKey: String): File {
        return File(rootDir, "${sanitize(cacheKey)}.bin")
    }

    fun getCachedFile(cacheKey: String): File? {
        val file = resolveCacheFile(cacheKey)
        return file.takeIf { it.exists() && it.isFile }
    }

    fun writeBase64(cacheKey: String, audioBase64: String): File? {
        return runCatching {
            val target = resolveCacheFile(cacheKey)
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                target.writeBytes(Base64.decode(audioBase64, Base64.NO_WRAP))
            }
            target
        }.getOrNull()
    }

    fun copyIntoCache(cacheKey: String, source: File): File? {
        return runCatching {
            val target = resolveCacheFile(cacheKey)
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
            }
            target
        }.getOrNull()
    }

    fun createTempFile(prefix: String): File {
        val dir = File(rootDir, "tmp").apply { mkdirs() }
        return File(dir, "${sanitize(prefix)}_${System.currentTimeMillis()}.bin")
    }

    fun stableHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun sanitize(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
