package com.chen.memorizewords.core.sprite

import android.content.res.AssetManager
import java.io.File
import javax.inject.Qualifier

@JvmInline
value class SpritePackId(val value: String) {
    init {
        require(value.matches(SAFE_ID)) { "Invalid sprite pack id: $value" }
    }

    companion object {
        private val SAFE_ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    }
}

@JvmInline
value class SpriteClipId(val value: String) {
    init {
        require(value.matches(SAFE_ID)) { "Invalid sprite clip id: $value" }
    }

    companion object {
        private val SAFE_ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    }
}

enum class SpritePlaybackMode {
    ONCE,
    LOOP,
    HOLD_LAST
}

data class SpriteAtlasSpec(
    val fileName: String,
    val width: Int,
    val height: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val columns: Int,
    val frameCount: Int
) {
    val rows: Int
        get() = if (frameCount <= 0 || columns <= 0) {
            0
        } else {
            ((frameCount.toLong() + columns - 1L) / columns).toInt()
        }
}

data class SpriteClipSpec(
    val id: SpriteClipId,
    val startFrame: Int,
    val frameCount: Int,
    val framesPerSecond: Int,
    val playbackMode: SpritePlaybackMode,
    val nextClipId: SpriteClipId? = null,
    val reversible: Boolean = false
) {
    val endFrameExclusive: Int
        get() = Math.addExact(startFrame, frameCount)

    val frameDurationNanos: Long
        get() {
            require(framesPerSecond > 0) { "framesPerSecond must be positive" }
            return 1_000_000_000L / framesPerSecond
        }

    fun frameIndices(reverse: Boolean = false): IntArray {
        return IntArray(frameCount) { offset ->
            if (reverse) {
                endFrameExclusive - 1 - offset
            } else {
                startFrame + offset
            }
        }
    }
}

data class SpritePackManifest(
    val schemaVersion: Int,
    val packId: SpritePackId,
    val packVersion: Int,
    val atlas: SpriteAtlasSpec,
    val clips: Map<SpriteClipId, SpriteClipSpec>,
    val semanticBindings: Map<String, SpriteClipId>,
    val fallbackClipId: SpriteClipId
) {
    fun clip(id: SpriteClipId): SpriteClipSpec = requireNotNull(clips[id]) {
        "Unknown sprite clip id: ${id.value}"
    }
}

sealed interface SpriteAtlasSource {
    data class BundledAsset(
        val assetManager: AssetManager,
        val assetPath: String
    ) : SpriteAtlasSource

    data class LocalFile(val file: File) : SpriteAtlasSource
}

data class SpritePack(
    val manifest: SpritePackManifest,
    val atlasSource: SpriteAtlasSource
)

interface SpritePackSource {
    suspend fun load(packId: SpritePackId): SpritePack?
}

interface SpritePackRepository {
    suspend fun get(packId: SpritePackId): SpritePack
}

fun interface SpritePackContractValidator {
    fun validate(manifest: SpritePackManifest)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadedSpriteSource

class SpritePackValidationException(message: String) : IllegalArgumentException(message)
