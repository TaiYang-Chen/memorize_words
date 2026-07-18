package com.chen.memorizewords.data.floating.remoteapi

import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.await
import com.chen.memorizewords.core.network.remote.RemoteResultAdapter
import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackResolution
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterPackRequest @Inject constructor(
    private val apiService: CharacterPackApiService,
    private val resultAdapter: RemoteResultAdapter
) {
    suspend fun getCatalog(): Result<List<CharacterPackCatalogItem>> {
        return resultAdapter.toResult {
            apiService.getCharacterPacks()
                .await<ApiResponse<List<CharacterPackDto>>, List<CharacterPackDto>>()
        }.map { items -> items.map(CharacterPackDto::toDomain) }
    }

    suspend fun resolveAppliedCharacterPack(): Result<CharacterPackResolution> {
        return resultAdapter.toResult {
            apiService.resolveAppliedCharacterPack()
                .await<ApiResponse<CharacterPackResolveDto>, CharacterPackResolveDto>()
        }.mapCatching(CharacterPackResolveDto::toDomain)
    }

    suspend fun applyCharacterPack(packId: String): Result<Unit> {
        return resultAdapter.toResult {
            apiService.applyCharacterPack(ApplyCharacterPackRequest(packId))
                .await<ApiResponse<Unit>, Unit>()
        }
    }
}

internal fun CharacterPackResolveDto.toDomain(): CharacterPackResolution = when (status) {
    "RESOLVED" -> CharacterPackResolution.Resolved(
        requireNotNull(pack) { "Resolved character pack response is missing pack" }.toDomain()
    )

    "SELECTION_REQUIRED" -> {
        require(pack == null) { "Selection-required character pack response must not include pack" }
        CharacterPackResolution.SelectionRequired
    }

    else -> throw IllegalArgumentException("Unsupported character pack resolve status: $status")
}

internal fun CharacterPackDto.toDomain(): CharacterPackCatalogItem {
    return CharacterPackCatalogItem(
        packId = packId,
        packVersion = packVersion,
        displayName = displayName,
        description = description,
        sortOrder = sortOrder,
        isDefault = isDefault,
        previewUrl = previewUrl,
        packageUrl = packageUrl,
        packageSha256 = packageSha256,
        packageSizeBytes = packageSizeBytes,
        manifestSchemaVersion = manifestSchemaVersion,
        updatedAtMs = updatedAtMs
    )
}
