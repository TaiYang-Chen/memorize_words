package com.chen.memorizewords.data.floating.repository

import android.content.Context
import com.chen.memorizewords.core.sprite.SpriteAtlasSource
import com.chen.memorizewords.core.sprite.SpritePack
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.sprite.SpritePackManifestParser
import com.chen.memorizewords.core.sprite.SpritePackSource
import com.chen.memorizewords.core.sprite.SpriteTextureId
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
        val primary = loadRevisionOrNull(packId, installed.packVersion, installed.installedDirectory)
        val fallback = if (installed.pendingRuntimeValidation) {
            val version = installed.lastKnownGoodVersion
            val directory = installed.lastKnownGoodDirectory
            if (version != null && directory != null) loadRevisionOrNull(packId, version, directory) else null
        } else null
        when {
            primary != null -> primary.copy(runtimeFallback = fallback?.asLastKnownGood())
            fallback != null -> fallback.asLastKnownGood()
            else -> throw IllegalStateException("Installed character pack has no loadable runtime revision")
        }
    }

    private fun loadRevisionOrNull(packId: SpritePackId, packVersion: Int, installedDirectory: String): SpritePack? =
        try { loadRevision(packId, packVersion, installedDirectory) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }

    private fun loadRevision(packId: SpritePackId, packVersion: Int, installedDirectory: String): SpritePack {
        val expectedRoot = File(context.filesDir, "character_packs/${packId.value}").canonicalFile
        val root = File(installedDirectory).canonicalFile
        require(root.parentFile == expectedRoot && CharacterPackLocalStore.isInstalledDirectoryForVersion(root.name, packVersion))
        val manifestFile = File(root, "manifest.json").canonicalFile
        require(manifestFile.parentFile == root)
        val manifest = CharacterPackManifestFileReader.parse(manifestFile, CharacterPackLocalStore.MAX_MANIFEST_BYTES, parser)
        require(manifest.packId == packId && manifest.packVersion == packVersion)
        val textureFiles = CharacterPackTextureFileValidator.validate(root, manifest, true, false)
        val textureSources: Map<SpriteTextureId, SpriteAtlasSource> = buildMap {
            textureFiles.forEach { (id, file) -> put(id, SpriteAtlasSource.LocalFile(file)) }
        }
        val fallbackTextureId = manifest.clip(manifest.fallbackClipId).textureId
        val compatibilitySource = requireNotNull(textureSources[fallbackTextureId])
        return SpritePack(manifest, compatibilitySource, textureSources)
    }

}
