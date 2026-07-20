package com.chen.memorizewords.data.floating.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.chen.memorizewords.core.sprite.SpriteAtlasSource
import com.chen.memorizewords.core.sprite.SpriteAtlasSpec
import com.chen.memorizewords.core.sprite.SpritePack
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.sprite.SpritePackManifestParser
import com.chen.memorizewords.core.sprite.SpritePackSource
import com.chen.memorizewords.data.floating.local.CharacterPackLocalStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DownloadedSpritePackSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: CharacterPackLocalStore
) : SpritePackSource {
    private val parser = SpritePackManifestParser()
    override suspend fun load(packId: SpritePackId): SpritePack? = withContext(Dispatchers.IO) {
        val installed = store.installed(packId.value) ?: return@withContext null
        try {
            val expectedRoot = File(
                context.filesDir,
                "character_packs/${packId.value}"
            ).canonicalFile
            val root = File(installed.installedDirectory).canonicalFile
            if (
                root.parentFile != expectedRoot ||
                !CharacterPackLocalStore.isInstalledDirectoryForVersion(
                    value = root.name,
                    packVersion = installed.packVersion
                )
            ) {
                return@withContext null
            }
            val manifestFile = File(root, "manifest.json").canonicalFile
            if (manifestFile.parentFile != root) {
                return@withContext null
            }
            val manifest = CharacterPackManifestFileReader.parse(
                file = manifestFile,
                maxBytes = CharacterPackLocalStore.MAX_MANIFEST_BYTES,
                parser = parser
            )
            if (manifest.packId != packId || manifest.packVersion != installed.packVersion) {
                return@withContext null
            }
            val atlas = File(root, manifest.atlas.fileName).canonicalFile
            if (atlas.parentFile != root || !atlas.isFile) return@withContext null
            CharacterPackWebpContainerValidator.validate(
                file = atlas,
                maxBytes = CharacterPackLocalStore.MAX_ATLAS_BYTES
            )
            CharacterPackAtlasDecoderValidator.validate(
                atlasFile = atlas,
                atlas = manifest.atlas,
                decodeLastFrame = true
            )
            SpritePack(manifest, SpriteAtlasSource.LocalFile(atlas))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}

internal object CharacterPackAtlasDecoderValidator {
    fun validate(
        atlasFile: File,
        atlas: SpriteAtlasSpec,
        decodeLastFrame: Boolean
    ) {
        atlasFile.inputStream().buffered().use { input ->
            val decoder = requireNotNull(BitmapRegionDecoder.newInstance(input, false)) {
                "Unable to create character atlas decoder"
            }
            try {
                require(decoder.width == atlas.width && decoder.height == atlas.height) {
                    "Character atlas dimensions mismatch"
                }
                decodeFrame(decoder, atlas, 0)
                if (decodeLastFrame && atlas.frameCount > 1) {
                    decodeFrame(decoder, atlas, atlas.frameCount - 1)
                }
            } finally {
                decoder.recycle()
            }
        }
    }

    private fun decodeFrame(
        decoder: BitmapRegionDecoder,
        atlas: SpriteAtlasSpec,
        frameIndex: Int
    ) {
        val column = frameIndex % atlas.columns
        val row = frameIndex / atlas.columns
        val region = Rect(
            column * atlas.frameWidth,
            row * atlas.frameHeight,
            (column + 1) * atlas.frameWidth,
            (row + 1) * atlas.frameHeight
        )
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val frame = requireNotNull(decoder.decodeRegion(region, options)) {
            "Unable to decode character atlas frame $frameIndex"
        }
        if (!frame.isRecycled) frame.recycle()
    }
}
