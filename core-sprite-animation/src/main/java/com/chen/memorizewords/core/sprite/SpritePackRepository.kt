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
        var primary: SpritePack? = null
        var candidateFailed = false
        for (source in sources) {
            try {
                source.load(packId)?.let { candidate ->
                    validateCandidate(candidate.manifest)
                    val currentPrimary = primary
                    val normalized = if (
                        currentPrimary == null &&
                        candidateFailed &&
                        candidate.runtimeRole == SpritePackRuntimeRole.PRIMARY
                    ) {
                        candidate.asLastKnownGood()
                    } else {
                        candidate
                    }
                    if (currentPrimary == null) {
                        if (
                            normalized.runtimeRole == SpritePackRuntimeRole.LAST_KNOWN_GOOD ||
                            normalized.runtimeFallback != null
                        ) {
                            return normalized
                        }
                        primary = normalized
                    } else if (candidate.manifest.packId == currentPrimary.manifest.packId) {
                        return currentPrimary.copy(
                            runtimeFallback = candidate.asLastKnownGood()
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A corrupt or incompatible source must not prevent later sources from resolving.
                if (primary == null) candidateFailed = true
            }
        }
        return primary
    }
}
