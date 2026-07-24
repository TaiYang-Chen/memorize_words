package com.chen.memorizewords.core.sprite

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

@JvmInline
value class SpriteTextureId(val value: String) {
    init {
        require(value.matches(SAFE_ID)) { "Invalid sprite texture id: $value" }
    }

    companion object {
        private val SAFE_ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    }
}

val DEFAULT_SPRITE_TEXTURE_ID = SpriteTextureId("standard")

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

sealed interface SpriteTextureSpec {
    val id: SpriteTextureId
    val fileName: String
    val frameWidth: Int
    val frameHeight: Int
    val frameCount: Int
}

data class WebpAtlasTextureSpec(
    override val id: SpriteTextureId,
    val atlas: SpriteAtlasSpec
) : SpriteTextureSpec {
    override val fileName: String
        get() = atlas.fileName
    override val frameWidth: Int
        get() = atlas.frameWidth
    override val frameHeight: Int
        get() = atlas.frameHeight
    override val frameCount: Int
        get() = atlas.frameCount
}

enum class BasisCompressionMode(val wireName: String) {
    UASTC("uastc");

    companion object {
        fun fromWireName(value: String): BasisCompressionMode? =
            values().firstOrNull { it.wireName == value.lowercase() }
    }
}

enum class SpriteColorSpace(val wireName: String) {
    SRGB("srgb"),
    LINEAR("linear");

    companion object {
        fun fromWireName(value: String): SpriteColorSpace? =
            values().firstOrNull { it.wireName == value.lowercase() }
    }
}

enum class SpriteAlphaMode(val wireName: String) {
    STRAIGHT("straight"),
    PREMULTIPLIED("premultiplied"),
    OPAQUE("opaque");

    companion object {
        fun fromWireName(value: String): SpriteAlphaMode? =
            values().firstOrNull { it.wireName == value.lowercase() }
    }
}

data class Ktx2PagedTextureSpec(
    override val id: SpriteTextureId,
    override val fileName: String,
    override val frameWidth: Int,
    override val frameHeight: Int,
    val pageWidth: Int,
    val pageHeight: Int,
    val columns: Int,
    val rows: Int,
    val pageCount: Int,
    override val frameCount: Int,
    val basisMode: BasisCompressionMode,
    val colorSpace: SpriteColorSpace,
    val alphaMode: SpriteAlphaMode
) : SpriteTextureSpec {
    val framesPerPage: Int
        get() = Math.multiplyExact(columns, rows)

    val requiredPageCount: Int
        get() = if (frameCount <= 0 || framesPerPage <= 0) {
            0
        } else {
            ((frameCount.toLong() + framesPerPage - 1L) / framesPerPage).toInt()
        }

    /** ETC2 RGBA8 and the GLES2 RGB/alpha dual-plane fallback both use 8 bits per texel. */
    val estimatedGpuResidentBytes: Long
        get() {
            val blocksWide = (pageWidth.toLong() + UASTC_BLOCK_EDGE - 1L) / UASTC_BLOCK_EDGE
            val blocksHigh = (pageHeight.toLong() + UASTC_BLOCK_EDGE - 1L) / UASTC_BLOCK_EDGE
            return Math.multiplyExact(
                Math.multiplyExact(
                    Math.multiplyExact(blocksWide, blocksHigh),
                    UASTC_BLOCK_BYTES
                ),
                pageCount.toLong()
            )
        }

    private companion object {
        const val UASTC_BLOCK_EDGE = 4L
        const val UASTC_BLOCK_BYTES = 16L
    }
}

data class SpriteClipSpec(
    val id: SpriteClipId,
    val startFrame: Int,
    val frameCount: Int,
    val framesPerSecond: Int,
    val playbackMode: SpritePlaybackMode,
    val nextClipId: SpriteClipId? = null,
    val reversible: Boolean = false,
    val textureId: SpriteTextureId = DEFAULT_SPRITE_TEXTURE_ID
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
    val fallbackClipId: SpriteClipId,
    /** Empty for schema v1, whose implicit texture is exposed through [texture]. */
    val textures: Map<SpriteTextureId, SpriteTextureSpec> = emptyMap(),
    val eventBindings: Map<String, SpriteClipId> = emptyMap()
) {
    fun clip(id: SpriteClipId): SpriteClipSpec = requireNotNull(clips[id]) {
        "Unknown sprite clip id: ${id.value}"
    }

    fun texture(id: SpriteTextureId): SpriteTextureSpec {
        textures[id]?.let { return it }
        if (schemaVersion == LEGACY_SCHEMA_VERSION && id == DEFAULT_SPRITE_TEXTURE_ID) {
            return WebpAtlasTextureSpec(id, atlas)
        }
        throw IllegalArgumentException("Unknown sprite texture id: ${id.value}")
    }

    companion object {
        const val LEGACY_SCHEMA_VERSION = 1
        const val KTX2_SCHEMA_VERSION = 2
    }
}

sealed interface SpriteAtlasSource {
    data class LocalFile(val file: File) : SpriteAtlasSource
}

enum class SpritePackRuntimeRole {
    PRIMARY,
    LAST_KNOWN_GOOD
}

data class SpritePack(
    val manifest: SpritePackManifest,
    val atlasSource: SpriteAtlasSource,
    val textureSources: Map<SpriteTextureId, SpriteAtlasSource> = emptyMap(),
    val runtimeRole: SpritePackRuntimeRole = SpritePackRuntimeRole.PRIMARY,
    val runtimeFallback: SpritePack? = null
) {
    fun textureSource(id: SpriteTextureId): SpriteAtlasSource? =
        textureSources[id] ?: atlasSource.takeIf { id == DEFAULT_SPRITE_TEXTURE_ID }

    fun asLastKnownGood(): SpritePack =
        copy(runtimeRole = SpritePackRuntimeRole.LAST_KNOWN_GOOD, runtimeFallback = null)
}

interface SpritePackSource {
    suspend fun load(packId: SpritePackId): SpritePack?
}

interface SpritePackRepository {
    suspend fun find(packId: SpritePackId): SpritePack?

    suspend fun get(packId: SpritePackId): SpritePack
}

fun interface SpritePackContractValidator {
    fun validate(manifest: SpritePackManifest)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadedSpriteSource

class SpritePackValidationException(message: String) : IllegalArgumentException(message)
