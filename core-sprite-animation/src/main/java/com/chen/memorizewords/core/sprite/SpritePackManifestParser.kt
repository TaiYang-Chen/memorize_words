package com.chen.memorizewords.core.sprite

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.Reader

class SpritePackManifestParser(
    private val constraints: SpritePackConstraints = SpritePackConstraints()
) {
    fun parse(reader: Reader): SpritePackManifest {
        val element = try {
            JsonParser.parseReader(reader)
        } catch (error: RuntimeException) {
            throw SpritePackValidationException("Malformed sprite manifest: ${error.message}")
        }
        if (!element.isJsonObject) {
            throw SpritePackValidationException("Sprite manifest root must be an object")
        }
        val root = element.asJsonObject
        val schemaVersion = root.requiredInt("schemaVersion")
        if (schemaVersion !in constraints.supportedSchemaVersions) {
            throw SpritePackValidationException("Unsupported schema version $schemaVersion")
        }
        val manifest = when (schemaVersion) {
            SpritePackManifest.KTX2_SCHEMA_VERSION -> parseKtx2Manifest(root)
            else -> throw SpritePackValidationException("Unsupported schema version $schemaVersion")
        }
        SpritePackValidator(constraints).validate(manifest)
        return manifest
    }

    private fun parseLegacyManifest(root: JsonObject): SpritePackManifest {
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
        return SpritePackManifest(
            schemaVersion = SpritePackManifest.LEGACY_SCHEMA_VERSION,
            packId = SpritePackId(root.requiredString("packId")),
            packVersion = root.requiredInt("packVersion"),
            atlas = atlas,
            clips = parseClips(root.requiredObject("clips"), requireTexture = false),
            semanticBindings = parseBindings(root.requiredObject("semanticBindings")),
            fallbackClipId = SpriteClipId(root.requiredString("fallbackClip"))
        )
    }

    private fun parseKtx2Manifest(root: JsonObject): SpritePackManifest {
        val textures = root.requiredObject("textures").entrySet().associate { (key, value) ->
            val id = SpriteTextureId(key)
            if (!value.isJsonObject) {
                throw SpritePackValidationException("Texture ${id.value} must be an object")
            }
            val json = value.asJsonObject
            val type = json.requiredString("type")
            if (type != KTX2_PAGED_TYPE) {
                throw SpritePackValidationException("Unsupported texture type $type")
            }
            id to Ktx2PagedTextureSpec(
                id = id,
                fileName = json.requiredString("file"),
                frameWidth = json.requiredInt("frameWidth"),
                frameHeight = json.requiredInt("frameHeight"),
                pageWidth = json.requiredInt("pageWidth"),
                pageHeight = json.requiredInt("pageHeight"),
                columns = json.requiredInt("columns"),
                rows = json.requiredInt("rows"),
                pageCount = json.requiredInt("pageCount"),
                frameCount = json.requiredInt("frameCount"),
                basisMode = BasisCompressionMode.fromWireName(json.requiredString("basisMode"))
                    ?: throw SpritePackValidationException("Unsupported Basis mode"),
                colorSpace = SpriteColorSpace.fromWireName(json.requiredString("colorSpace"))
                    ?: throw SpritePackValidationException("Unsupported texture color space"),
                alphaMode = SpriteAlphaMode.fromWireName(json.requiredString("alphaMode"))
                    ?: throw SpritePackValidationException("Unsupported texture alpha mode")
            )
        }
        val clips = parseClips(root.requiredObject("clips"), requireTexture = true)
        val fallbackClipId = SpriteClipId(root.requiredString("fallbackClip"))
        val fallbackClip = clips[fallbackClipId]
            ?: throw SpritePackValidationException("Fallback clip is missing")
        val fallbackTexture = textures[fallbackClip.textureId]
            ?: throw SpritePackValidationException("Fallback clip texture is missing")
        return SpritePackManifest(
            schemaVersion = SpritePackManifest.KTX2_SCHEMA_VERSION,
            packId = SpritePackId(root.requiredString("packId")),
            packVersion = root.requiredInt("packVersion"),
            // Kept as a compatibility projection for schema-v1 consumers. V2 renderers use textures.
            atlas = fallbackTexture.toCompatibilityAtlas(),
            clips = clips,
            semanticBindings = parseBindings(root.requiredObject("semanticBindings")),
            fallbackClipId = fallbackClipId,
            textures = textures,
            eventBindings = root.optionalObject("eventBindings")?.let(::parseBindings).orEmpty()
        )
    }

    private fun parseClips(
        clipsObject: JsonObject,
        requireTexture: Boolean
    ): Map<SpriteClipId, SpriteClipSpec> = clipsObject.entrySet().associate { (key, value) ->
        val id = SpriteClipId(key)
        if (!value.isJsonObject) {
            throw SpritePackValidationException("Clip ${id.value} must be an object")
        }
        val json = value.asJsonObject
        id to SpriteClipSpec(
            id = id,
            startFrame = json.requiredInt("startFrame"),
            frameCount = json.requiredInt("frameCount"),
            framesPerSecond = json.requiredInt("fps"),
            playbackMode = when (val mode = json.requiredString("mode")) {
                "once" -> SpritePlaybackMode.ONCE
                "loop" -> SpritePlaybackMode.LOOP
                "hold_last" -> SpritePlaybackMode.HOLD_LAST
                else -> throw SpritePackValidationException("Unsupported playback mode $mode")
            },
            nextClipId = json.optionalString("next")?.let(::SpriteClipId),
            reversible = json.optionalBoolean("reversible") ?: false,
            textureId = if (requireTexture) {
                json.optionalString("texture")?.let(::SpriteTextureId)
                    ?: DEFAULT_SPRITE_TEXTURE_ID
            } else {
                DEFAULT_SPRITE_TEXTURE_ID
            }
        )
    }

    private fun parseBindings(json: JsonObject): Map<String, SpriteClipId> =
        json.entrySet().associate { (key, value) ->
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
                throw SpritePackValidationException("Binding $key must name a clip")
            }
            key to SpriteClipId(value.asString)
        }

    private fun SpriteTextureSpec.toCompatibilityAtlas(): SpriteAtlasSpec = when (this) {
        is WebpAtlasTextureSpec -> atlas
        is Ktx2PagedTextureSpec -> SpriteAtlasSpec(
            fileName = fileName,
            width = pageWidth,
            height = pageHeight,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            columns = columns,
            frameCount = frameCount
        )
    }

    private companion object {
        const val KTX2_PAGED_TYPE = "ktx2_paged"
    }
}

data class SpritePackConstraints(
    val supportedSchemaVersions: Set<Int> = setOf(
        SpritePackManifest.KTX2_SCHEMA_VERSION
    ),
    val maxAtlasEdge: Int = 4_096,
    val maxTexturePageEdge: Int = 2_048,
    val maxTextureCount: Int = 16,
    val maxTexturePageCount: Int = 16,
    val maxFrameCount: Int = 256,
    val maxFrameEdge: Int = 1_024,
    val maxFramesPerSecond: Int = 60,
    val maxDecodedLoopBytes: Long = 12L * 1_024L * 1_024L,
    val maxResidentAnimationBytes: Long = 18L * 1_024L * 1_024L,
    val maxResidentGpuTextureBytes: Long = 16L * 1_024L * 1_024L
)

class SpritePackValidator(
    private val constraints: SpritePackConstraints = SpritePackConstraints()
) {
    fun validate(manifest: SpritePackManifest) {
        requireManifest(manifest.schemaVersion in constraints.supportedSchemaVersions) {
            "Unsupported schema version ${manifest.schemaVersion}"
        }
        requireManifest(manifest.packVersion > 0) { "packVersion must be positive" }
        when (manifest.schemaVersion) {
            SpritePackManifest.KTX2_SCHEMA_VERSION -> validateKtx2Textures(manifest)
        }
        requireManifest(manifest.clips.isNotEmpty()) { "At least one clip is required" }
        requireManifest(manifest.fallbackClipId in manifest.clips) { "Fallback clip is missing" }
        manifest.clips.forEach { (mapKey, clip) -> validateClip(manifest, mapKey, clip) }
        validateBindings("Semantic", manifest.semanticBindings, manifest.clips)
        validateBindings("Event", manifest.eventBindings, manifest.clips)
    }

    private fun validateLegacyAtlas(manifest: SpritePackManifest) {
        val atlas = manifest.atlas
        requireManifest(manifest.textures.isEmpty()) {
            "Schema v1 must use its implicit WebP atlas"
        }
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
    }

    private fun validateKtx2Textures(manifest: SpritePackManifest) {
        requireManifest(manifest.textures.size in 1..constraints.maxTextureCount) {
            "Schema v2 requires a bounded texture set"
        }
        val fileNames = HashSet<String>()
        manifest.textures.forEach { (mapKey, texture) ->
            requireManifest(mapKey == texture.id) {
                "Texture map key ${mapKey.value} does not match texture id ${texture.id.value}"
            }
            requireManifest(texture is Ktx2PagedTextureSpec) {
                "Schema v2 only supports KTX2 paged textures"
            }
            val ktx2 = texture as Ktx2PagedTextureSpec
            requireManifest(ktx2.fileName.matches(SAFE_FILE_NAME) && ktx2.fileName.endsWith(".ktx2")) {
                "Unsafe KTX2 texture file name"
            }
            requireManifest(fileNames.add(ktx2.fileName)) { "Texture files must be unique" }
            requireManifest(ktx2.frameWidth in 1..constraints.maxFrameEdge) {
                "Texture frame width is invalid"
            }
            requireManifest(ktx2.frameHeight in 1..constraints.maxFrameEdge) {
                "Texture frame height is invalid"
            }
            requireManifest(ktx2.pageWidth in 1..constraints.maxTexturePageEdge) {
                "Texture page width is invalid"
            }
            requireManifest(ktx2.pageHeight in 1..constraints.maxTexturePageEdge) {
                "Texture page height is invalid"
            }
            requireManifest(ktx2.frameCount in 1..constraints.maxFrameCount) {
                "Texture frame count is invalid"
            }
            requireManifest(ktx2.columns > 0 && ktx2.rows > 0) {
                "Texture grid is invalid"
            }
            requireManifest(ktx2.columns.toLong() * ktx2.frameWidth <= ktx2.pageWidth.toLong()) {
                "Texture columns exceed page width"
            }
            requireManifest(ktx2.rows.toLong() * ktx2.frameHeight <= ktx2.pageHeight.toLong()) {
                "Texture rows exceed page height"
            }
            requireManifest(ktx2.pageCount in 1..constraints.maxTexturePageCount) {
                "Texture page count is invalid"
            }
            requireManifest(ktx2.pageCount == ktx2.requiredPageCount) {
                "Texture page count does not match its frame grid"
            }
            requireManifest(ktx2.basisMode == BasisCompressionMode.UASTC) {
                "Schema v2 requires UASTC textures"
            }
            requireManifest(ktx2.colorSpace == SpriteColorSpace.SRGB) {
                "Schema v2 requires sRGB textures"
            }
            requireManifest(ktx2.alphaMode == SpriteAlphaMode.STRAIGHT) {
                "Schema v2 requires straight alpha source textures"
            }
            requireManifest(ktx2.estimatedGpuResidentBytes <= constraints.maxResidentGpuTextureBytes) {
                "Texture ${ktx2.id.value} exceeds the resident GPU budget"
            }
        }
    }

    private fun validateClip(
        manifest: SpritePackManifest,
        mapKey: SpriteClipId,
        clip: SpriteClipSpec
    ) {
        requireManifest(mapKey == clip.id) {
            "Clip map key ${mapKey.value} does not match clip id ${clip.id.value}"
        }
        requireManifest(clip.startFrame >= 0) { "Clip ${clip.id.value} starts before frame zero" }
        requireManifest(clip.frameCount > 0) { "Clip ${clip.id.value} is empty" }
        val textureFrameCount = if (manifest.schemaVersion == SpritePackManifest.LEGACY_SCHEMA_VERSION) {
            requireManifest(clip.textureId == DEFAULT_SPRITE_TEXTURE_ID) {
                "Schema v1 clips must use the implicit atlas"
            }
            manifest.atlas.frameCount
        } else {
            val texture = manifest.textures[clip.textureId]
            requireManifest(texture != null) { "Clip ${clip.id.value} has a missing texture" }
            checkNotNull(texture).frameCount
        }
        val endFrameExclusive = clip.startFrame.toLong() + clip.frameCount
        requireManifest(endFrameExclusive <= textureFrameCount.toLong()) {
            "Clip ${clip.id.value} exceeds texture frame count"
        }
        requireManifest(clip.framesPerSecond in 1..constraints.maxFramesPerSecond) {
            "Clip ${clip.id.value} has invalid fps"
        }
        if (
            manifest.schemaVersion == SpritePackManifest.LEGACY_SCHEMA_VERSION &&
            clip.playbackMode == SpritePlaybackMode.LOOP
        ) {
            val frameBytes = manifest.atlas.frameWidth.toLong() * manifest.atlas.frameHeight *
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

    private fun validateBindings(
        label: String,
        bindings: Map<String, SpriteClipId>,
        clips: Map<SpriteClipId, SpriteClipSpec>
    ) {
        bindings.forEach { (key, clipId) ->
            requireManifest(key.matches(SAFE_BINDING_KEY)) { "$label binding key is unsafe" }
            requireManifest(clipId in clips) { "$label binding points to a missing clip" }
        }
    }

    private fun requireManifest(value: Boolean, lazyMessage: () -> String) {
        if (!value) throw SpritePackValidationException(lazyMessage())
    }

    companion object {
        private const val ARGB_8888_BYTES_PER_PIXEL = 4L
        private const val MIN_TRANSITION_RESIDENT_FRAMES = 6L
        private val SAFE_FILE_NAME = Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}")
        private val SAFE_BINDING_KEY = Regex("[a-z0-9][a-z0-9_.-]{0,63}")
    }
}

private fun JsonObject.requiredObject(name: String): JsonObject {
    val value = get(name)
    if (value == null || value.isJsonNull || !value.isJsonObject) {
        throw SpritePackValidationException("Missing object: $name")
    }
    return value.asJsonObject
}

private fun JsonObject.optionalObject(name: String): JsonObject? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    if (!value.isJsonObject) throw SpritePackValidationException("Invalid object: $name")
    return value.asJsonObject
}

private fun JsonObject.requiredString(name: String): String {
    val value = get(name)
    if (
        value == null || value.isJsonNull || !value.isJsonPrimitive ||
        !value.asJsonPrimitive.isString
    ) {
        throw SpritePackValidationException("Missing string: $name")
    }
    return value.asString
}

private fun JsonObject.requiredInt(name: String): Int {
    val value = get(name)
    if (value == null || value.isJsonNull || !value.isJsonPrimitive) {
        throw SpritePackValidationException("Missing integer: $name")
    }
    val primitive = value.asJsonPrimitive
    if (!primitive.isNumber || !primitive.asString.matches(INTEGER_PATTERN)) {
        throw SpritePackValidationException("Invalid integer: $name")
    }
    return primitive.asString.toIntOrNull()
        ?: throw SpritePackValidationException("Integer is out of range: $name")
}

private fun JsonObject.optionalString(name: String): String? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
        throw SpritePackValidationException("Invalid string: $name")
    }
    return value.asString
}

private fun JsonObject.optionalBoolean(name: String): Boolean? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) {
        throw SpritePackValidationException("Invalid boolean: $name")
    }
    return value.asBoolean
}

private val INTEGER_PATTERN = Regex("-?(0|[1-9][0-9]*)")
