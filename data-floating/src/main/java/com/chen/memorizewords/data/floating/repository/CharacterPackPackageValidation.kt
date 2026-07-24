package com.chen.memorizewords.data.floating.repository

import com.chen.memorizewords.core.sprite.Ktx2BasisRuntimeValidator
import com.chen.memorizewords.core.sprite.Ktx2HeaderConstraints
import com.chen.memorizewords.core.sprite.Ktx2HeaderParser
import com.chen.memorizewords.core.sprite.Ktx2PagedTextureHeaderValidator
import com.chen.memorizewords.core.sprite.Ktx2PagedTextureSpec
import com.chen.memorizewords.core.sprite.SpritePackManifest
import com.chen.memorizewords.core.sprite.SpriteTextureId
import com.chen.memorizewords.core.sprite.WebpAtlasTextureSpec
import com.chen.memorizewords.data.floating.local.CharacterPackLocalStore
import java.io.File

internal object CharacterPackPackageLayout {
    const val MANIFEST_FILE_NAME = "manifest.json"

    fun expectedEntries(manifest: SpritePackManifest): Set<String> =
        buildSet {
            add(MANIFEST_FILE_NAME)
            addAll(referencedTextureFiles(manifest).values)
        }

    fun referencedTextureFiles(manifest: SpritePackManifest): Map<SpriteTextureId, String> =
        when (manifest.schemaVersion) {
            SpritePackManifest.KTX2_SCHEMA_VERSION ->
                manifest.textures.mapValues { (_, texture) -> texture.fileName }
            else -> throw IllegalArgumentException(
                "Unsupported character manifest schema ${manifest.schemaVersion}"
            )
        }
}

internal object CharacterPackTextureFileValidator {
    fun validate(
        root: File,
        manifest: SpritePackManifest,
        decodeLastWebpFrame: Boolean,
        validateKtx2RuntimeReadiness: Boolean
    ): Map<SpriteTextureId, File> {
        val canonicalRoot = root.canonicalFile
        return CharacterPackPackageLayout
            .referencedTextureFiles(manifest)
            .mapValues { (textureId, fileName) ->
            val textureFile = File(canonicalRoot, fileName).canonicalFile
            require(textureFile.parentFile == canonicalRoot && textureFile.isFile) {
                "Character texture is missing"
            }
            when (val texture = manifest.texture(textureId)) {
                is WebpAtlasTextureSpec -> throw IllegalArgumentException(
                    "WebP character packages are no longer supported"
                )
                is Ktx2PagedTextureSpec -> {
                    CharacterPackKtx2ContainerValidator.validate(
                        file = textureFile,
                        spec = texture,
                        maxBytes = CharacterPackLocalStore.MAX_ATLAS_BYTES
                    )
                    if (validateKtx2RuntimeReadiness) {
                        try {
                            Ktx2BasisRuntimeValidator.validateAllPages(textureFile, texture)
                        } catch (error: LinkageError) {
                            throw IllegalStateException(
                                "KTX2 runtime is unavailable for this device ABI",
                                error
                            )
                        }
                    }
                }
            }
            textureFile
        }
    }
}

internal object CharacterPackKtx2ContainerValidator {
    fun validate(
        file: File,
        spec: Ktx2PagedTextureSpec,
        maxBytes: Long
    ) {
        val actualLength = file.length()
        require(
            file.isFile &&
                actualLength in Ktx2HeaderParser.FIXED_HEADER_BYTES.toLong()..maxBytes
        ) {
            "Character KTX2 size is invalid"
        }
        val parser = Ktx2HeaderParser(
            Ktx2HeaderConstraints(
                maxDimension = maxOf(spec.pageWidth, spec.pageHeight),
                maxLayerCount = spec.pageCount,
                maxLevelCount = 1,
                maxContainerBytes = maxBytes
            )
        )
        val header = file.inputStream().buffered().use { input ->
            parser.parse(input, actualLength)
        }
        Ktx2PagedTextureHeaderValidator().validate(spec, header)
    }
}
