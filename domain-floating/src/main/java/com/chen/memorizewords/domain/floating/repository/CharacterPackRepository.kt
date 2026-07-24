package com.chen.memorizewords.domain.floating.repository

import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackResolution
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import kotlinx.coroutines.flow.Flow

interface CharacterPackRepository {
    fun observeCatalog(): Flow<List<CharacterPackCatalogItem>>
    fun observeInstalled(): Flow<Map<String, InstalledCharacterPack>>
    fun observeDownloads(): Flow<Map<String, CharacterPackDownloadState>>

    suspend fun refreshCatalog(): Result<Unit>
    suspend fun resolveAppliedCharacterPack(): Result<CharacterPackResolution>
    suspend fun applyCharacterPack(packId: String): Result<Unit>
    suspend fun startDownload(
        item: CharacterPackCatalogItem,
        selectAfterInstall: Boolean,
        activationRequestId: String? = null
    ): Result<Unit>
    suspend fun acknowledgeManagementDownloadCompletion(
        packId: String,
        downloadRequestId: String
    ): Boolean
    suspend fun cancelDownload(packId: String)
    suspend fun deleteInstalled(packId: String)
    suspend fun acknowledgeRuntimeReady(
        packId: String,
        packVersion: Int,
        installedDirectory: String
    ): Boolean = false
    suspend fun rollbackPendingRuntimeValidation(
        packId: String,
        packVersion: Int,
        installedDirectory: String
    ): Boolean = false
    suspend fun getInstalled(packId: String): InstalledCharacterPack?
    suspend fun isInstalledUsable(packId: String): Boolean
}
