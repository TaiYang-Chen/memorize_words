package com.chen.memorizewords.core.sprite

import kotlinx.coroutines.CancellationException

class CompositeSpritePackRepository(
    private val sources: List<SpritePackSource>,
    private val fallbackPackId: SpritePackId? = null,
    private val validateCandidate: (SpritePackManifest) -> Unit = {}
) : SpritePackRepository {
    override suspend fun find(packId: SpritePackId): SpritePack? {
        resolve(packId)?.let { return it }
        if (fallbackPackId != null && packId != fallbackPackId) {
            resolve(fallbackPackId)?.let { return it }
        }
        return null
    }

    override suspend fun get(packId: SpritePackId): SpritePack = requireNotNull(find(packId)) {
        "No valid sprite pack found for ${packId.value}"
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
