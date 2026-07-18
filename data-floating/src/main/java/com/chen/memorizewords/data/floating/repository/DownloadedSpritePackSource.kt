package com.chen.memorizewords.data.floating.repository

import com.chen.memorizewords.core.sprite.SpriteAtlasSource
import com.chen.memorizewords.core.sprite.SpritePack
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.sprite.SpritePackManifestParser
import com.chen.memorizewords.core.sprite.SpritePackSource
import com.chen.memorizewords.data.floating.local.CharacterPackLocalStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DownloadedSpritePackSource @Inject constructor(
    private val store: CharacterPackLocalStore
) : SpritePackSource {
    private val parser = SpritePackManifestParser()
    override suspend fun load(packId: SpritePackId): SpritePack? = withContext(Dispatchers.IO) {
        val installed = store.installed(packId.value) ?: return@withContext null
        try {
            val root = File(installed.installedDirectory)
            val manifestFile = File(root, "manifest.json")
            val manifest = manifestFile.bufferedReader().use(parser::parse)
            if (manifest.packId != packId || manifest.packVersion != installed.packVersion) {
                return@withContext null
            }
            val atlas = File(root, manifest.atlas.fileName)
            if (!atlas.isFile) return@withContext null
            SpritePack(manifest, SpriteAtlasSource.LocalFile(atlas))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
