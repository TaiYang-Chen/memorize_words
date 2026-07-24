package com.chen.memorizewords.data.floating.remoteapi

import com.chen.memorizewords.core.network.http.ApiResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface CharacterPackApiService {
    @GET("app/character-packs")
    fun getCharacterPacks(
        @Query("supportedManifestSchemas") supportedManifestSchemas: String =
            CharacterPackClientCapabilities.MANIFEST_SCHEMAS,
        @Query("supportedRenderers") supportedRenderers: String =
            CharacterPackClientCapabilities.RENDERERS
    ): Call<ApiResponse<List<CharacterPackDto>>>

    @POST("app/character-packs/resolve")
    fun resolveAppliedCharacterPack(
        @Query("supportedManifestSchemas") supportedManifestSchemas: String =
            CharacterPackClientCapabilities.MANIFEST_SCHEMAS,
        @Query("supportedRenderers") supportedRenderers: String =
            CharacterPackClientCapabilities.RENDERERS
    ): Call<ApiResponse<CharacterPackResolveDto>>

    @PUT("app/character-packs/applied")
    fun applyCharacterPack(@Body request: ApplyCharacterPackRequest): Call<ApiResponse<Unit>>
}

data class CharacterPackDto(
    val packId: String,
    val packVersion: Int,
    val displayName: String,
    val description: String? = null,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val previewUrl: String,
    val packageUrl: String,
    val packageSha256: String,
    val packageSizeBytes: Long,
    val manifestSchemaVersion: Int,
    val updatedAtMs: Long
)

data class CharacterPackResolveDto(
    val status: String,
    val pack: CharacterPackDto? = null
)

data class ApplyCharacterPackRequest(
    val packId: String
)
