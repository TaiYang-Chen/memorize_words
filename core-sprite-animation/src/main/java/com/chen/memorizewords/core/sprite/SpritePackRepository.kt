package com.chen.memorizewords.core.sprite

import android.content.res.AssetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BundledSpritePackSource(
    private val assetManager: AssetManager,
    private val rootPath: String = "characters",
    private val parser: SpritePackManifestParser = SpritePackManifestParser()
) : SpritePackSource {
    override suspend fun load(packId: SpritePackId): SpritePack? = withContext(Dispatchers.IO) {
        val packRoot = "$rootPath/${packId.value}"
        val manifestPath = "$packRoot/manifest.json"
        try {
            val manifest = assetManager.open(manifestPath).bufferedReader().use(parser::parse)
            require(manifest.packId == packId) { "Manifest pack id does not match directory" }
            SpritePack(
                manifest = manifest,
                atlasSource = SpriteAtlasSource.BundledAsset(
                    assetManager = assetManager,
                    assetPath = "$packRoot/${manifest.atlas.fileName}"
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}

class CompositeSpritePackRepository(
    private val sources: List<SpritePackSource>,
    private val fallbackPackId: SpritePackId,
    private val validateCandidate: (SpritePackManifest) -> Unit = {}
) : SpritePackRepository {
    override suspend fun get(packId: SpritePackId): SpritePack {
        resolve(packId)?.let { return it }
        if (packId != fallbackPackId) {
            resolve(fallbackPackId)?.let { return it }
        }
        error("No valid sprite pack found for ${packId.value} or fallback ${fallbackPackId.value}")
    }

    private suspend fun resolve(packId: SpritePackId): SpritePack? {
        for (source in sources) {
            try {
                source.load(packId)?.let { candidate ->
                    validateCandidate(candidate.manifest)
                    return candidate
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A corrupt or incompatible source must not prevent later sources from resolving.
            }
        }
        return null
    }
}
