package com.chen.memorizewords.core.sprite

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.Reader

class SpritePackManifestParser(
    private val constraints: SpritePackConstraints = SpritePackConstraints()
) {
    fun parse(reader: Reader): SpritePackManifest {
        val root = JsonParser.parseReader(reader).asJsonObject
        val atlasObject = root.requiredObject("atlas")
        val atlas = SpriteAtlasSpec(
            fileName = atlasObject.requiredString("file"),
            width = atlasObject.requiredInt("width"),
            height = atlasObject.requiredInt("height"),
            frameWidth = atlasObject.requiredInt("frameWidth"),
            frameHeight = atlasObject.requiredInt("frameHeight"),
            columns = atlasObject.requiredInt("columns"),
            frameCount = atlasObject.requiredInt("frameCount")
        )
        val clips = root.requiredObject("clips").entrySet().associate { (key, value) ->
            val id = SpriteClipId(key)
            val json = value.asJsonObject
            id to SpriteClipSpec(
                id = id,
                startFrame = json.requiredInt("startFrame"),
                frameCount = json.requiredInt("frameCount"),
                framesPerSecond = json.requiredInt("fps"),
                playbackMode = SpritePlaybackMode.valueOf(
                    json.requiredString("mode").uppercase()
                ),
                nextClipId = json.optionalString("next")?.let(::SpriteClipId),
                reversible = json.optionalBoolean("reversible") ?: false
            )
        }
        val bindings = root.requiredObject("semanticBindings").entrySet().associate { (key, value) ->
            key to SpriteClipId(value.asString)
        }
        val manifest = SpritePackManifest(
            schemaVersion = root.requiredInt("schemaVersion"),
            packId = SpritePackId(root.requiredString("packId")),
            packVersion = root.requiredInt("packVersion"),
            atlas = atlas,
            clips = clips,
            semanticBindings = bindings,
            fallbackClipId = SpriteClipId(root.requiredString("fallbackClip"))
        )
        SpritePackValidator(constraints).validate(manifest)
        return manifest
    }
}

data class SpritePackConstraints(
    val supportedSchemaVersion: Int = 1,
    val maxAtlasEdge: Int = 4_096,
    val maxFrameCount: Int = 256,
    val maxFrameEdge: Int = 1_024,
    val maxFramesPerSecond: Int = 60,
    val maxDecodedLoopBytes: Long = 12L * 1_024L * 1_024L,
    val maxResidentAnimationBytes: Long = 18L * 1_024L * 1_024L
)

class SpritePackValidator(
    private val constraints: SpritePackConstraints = SpritePackConstraints()
) {
    fun validate(manifest: SpritePackManifest) {
        val atlas = manifest.atlas
        requireManifest(manifest.schemaVersion == constraints.supportedSchemaVersion) {
            "Unsupported schema version ${manifest.schemaVersion}"
        }
        requireManifest(manifest.packVersion > 0) { "packVersion must be positive" }
        requireManifest(atlas.fileName.matches(SAFE_FILE_NAME)) { "Unsafe atlas file name" }
        requireManifest(atlas.width in 1..constraints.maxAtlasEdge) { "Atlas width is invalid" }
        requireManifest(atlas.height in 1..constraints.maxAtlasEdge) { "Atlas height is invalid" }
        requireManifest(atlas.frameWidth in 1..constraints.maxFrameEdge) { "Frame width is invalid" }
        requireManifest(atlas.frameHeight in 1..constraints.maxFrameEdge) { "Frame height is invalid" }
        requireManifest(atlas.frameCount in 1..constraints.maxFrameCount) { "Frame count is invalid" }
        requireManifest(atlas.columns in 1..atlas.frameCount) {
            "Atlas columns must be within the frame count"
        }
        requireManifest(atlas.columns.toLong() * atlas.frameWidth <= atlas.width.toLong()) {
            "Atlas columns exceed width"
        }
        requireManifest(atlas.rows.toLong() * atlas.frameHeight <= atlas.height.toLong()) {
            "Atlas rows exceed height"
        }
        requireManifest(manifest.clips.isNotEmpty()) { "At least one clip is required" }
        requireManifest(manifest.fallbackClipId in manifest.clips) { "Fallback clip is missing" }
        manifest.clips.forEach { (mapKey, clip) ->
            requireManifest(mapKey == clip.id) {
                "Clip map key ${mapKey.value} does not match clip id ${clip.id.value}"
            }
            requireManifest(clip.startFrame >= 0) { "Clip ${clip.id.value} starts before frame zero" }
            requireManifest(clip.frameCount > 0) { "Clip ${clip.id.value} is empty" }
            val endFrameExclusive = clip.startFrame.toLong() + clip.frameCount
            requireManifest(endFrameExclusive <= atlas.frameCount.toLong()) {
                "Clip ${clip.id.value} exceeds atlas frame count"
            }
            requireManifest(clip.framesPerSecond in 1..constraints.maxFramesPerSecond) {
                "Clip ${clip.id.value} has invalid fps"
            }
            if (clip.playbackMode == SpritePlaybackMode.LOOP) {
                val frameBytes = atlas.frameWidth.toLong() * atlas.frameHeight *
                    ARGB_8888_BYTES_PER_PIXEL
                val decodedBytes = frameBytes * clip.frameCount
                requireManifest(decodedBytes <= constraints.maxDecodedLoopBytes) {
                    "Loop clip ${clip.id.value} exceeds the decoded cache budget"
                }
                val minimumResidentBytes = decodedBytes +
                    (frameBytes * MIN_TRANSITION_RESIDENT_FRAMES)
                requireManifest(minimumResidentBytes <= constraints.maxResidentAnimationBytes) {
                    "Loop clip ${clip.id.value} leaves no safe transition memory budget"
                }
            }
            clip.nextClipId?.let { next ->
                requireManifest(next in manifest.clips) { "Clip ${clip.id.value} has missing next clip" }
            }
        }
        manifest.semanticBindings.values.forEach { clipId ->
            requireManifest(clipId in manifest.clips) { "Semantic binding points to a missing clip" }
        }
    }

    private fun requireManifest(value: Boolean, lazyMessage: () -> String) {
        if (!value) throw SpritePackValidationException(lazyMessage())
    }

    companion object {
        private const val ARGB_8888_BYTES_PER_PIXEL = 4L
        private const val MIN_TRANSITION_RESIDENT_FRAMES = 6L
        private val SAFE_FILE_NAME = Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}")
    }
}

private fun JsonObject.requiredObject(name: String): JsonObject =
    getAsJsonObject(name) ?: throw SpritePackValidationException("Missing object: $name")

private fun JsonObject.requiredString(name: String): String =
    get(name)?.takeUnless { it.isJsonNull }?.asString
        ?: throw SpritePackValidationException("Missing string: $name")

private fun JsonObject.requiredInt(name: String): Int =
    get(name)?.takeUnless { it.isJsonNull }?.asInt
        ?: throw SpritePackValidationException("Missing integer: $name")

private fun JsonObject.optionalString(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString

private fun JsonObject.optionalBoolean(name: String): Boolean? =
    get(name)?.takeUnless { it.isJsonNull }?.asBoolean
