package com.chen.memorizewords.data.floating.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chen.memorizewords.data.floating.local.CharacterPackLocalStore
import com.chen.memorizewords.data.floating.remoteapi.CharacterPackRequest
import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CharacterPackRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val request: CharacterPackRequest,
    private val store: CharacterPackLocalStore
) : CharacterPackRepository {
    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun observeCatalog(): Flow<List<CharacterPackCatalogItem>> = store.observeCatalog()
    override fun observeInstalled(): Flow<Map<String, InstalledCharacterPack>> = store.observeInstalled()
    override fun observeDownloads(): Flow<Map<String, CharacterPackDownloadState>> = store.observeDownloads()

    override suspend fun refreshCatalog(): Result<Unit> {
        return request.getCatalog().map { items -> store.replaceCatalog(items) }
    }

    override suspend fun startDownload(item: CharacterPackCatalogItem, selectAfterInstall: Boolean) {
        if (!CharacterPackLocalStore.isSafePackId(item.packId) || item.packVersion <= 0) return
        store.putDownload(
            CharacterPackDownloadState(
                packId = item.packId,
                packVersion = item.packVersion,
                status = CharacterPackDownloadStatus.QUEUED,
                totalBytes = item.packageSizeBytes,
                selectAfterInstall = selectAfterInstall
            )
        )
        val work = OneTimeWorkRequestBuilder<CharacterPackDownloadWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    CharacterPackWork.KEY_PACK_ID to item.packId,
                    CharacterPackWork.KEY_PACK_VERSION to item.packVersion,
                    CharacterPackWork.KEY_DISPLAY_NAME to item.displayName,
                    CharacterPackWork.KEY_DESCRIPTION to item.description,
                    CharacterPackWork.KEY_PREVIEW_URL to item.previewUrl,
                    CharacterPackWork.KEY_PACKAGE_URL to item.packageUrl,
                    CharacterPackWork.KEY_PACKAGE_SHA256 to item.packageSha256,
                    CharacterPackWork.KEY_PACKAGE_SIZE to item.packageSizeBytes,
                    CharacterPackWork.KEY_SELECT_AFTER_INSTALL to selectAfterInstall
                )
            )
            .addTag(CharacterPackWork.tag(item.packId))
            .build()
        workManager.enqueueUniqueWork(
            CharacterPackWork.uniqueName(item.packId),
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    override suspend fun cancelDownload(packId: String) {
        workManager.cancelUniqueWork(CharacterPackWork.uniqueName(packId))
        val previous = store.download(packId)
        store.putDownload(
            (previous ?: CharacterPackDownloadState(packId)).copy(
                status = CharacterPackDownloadStatus.CANCELLED,
                errorMessage = null
            )
        )
        withContext(Dispatchers.IO) {
            File(context.cacheDir, "character_packs/$packId").deleteRecursively()
        }
    }

    override suspend fun deleteInstalled(packId: String) {
        if (packId == "green_pet") return
        cancelDownload(packId)
        withContext(Dispatchers.IO) {
            File(context.filesDir, "character_packs/$packId").deleteRecursively()
        }
        store.removeInstalled(packId)
        store.removeDownload(packId)
    }

    override suspend fun getInstalled(packId: String): InstalledCharacterPack? = store.installed(packId)
}

internal object CharacterPackWork {
    const val KEY_PACK_ID = "pack_id"
    const val KEY_PACK_VERSION = "pack_version"
    const val KEY_DISPLAY_NAME = "display_name"
    const val KEY_DESCRIPTION = "description"
    const val KEY_PREVIEW_URL = "preview_url"
    const val KEY_PACKAGE_URL = "package_url"
    const val KEY_PACKAGE_SHA256 = "package_sha256"
    const val KEY_PACKAGE_SIZE = "package_size"
    const val KEY_SELECT_AFTER_INSTALL = "select_after_install"
    const val KEY_PROGRESS = "progress"

    fun uniqueName(packId: String) = "character_pack_download_$packId"
    fun tag(packId: String) = "character_pack_$packId"
}
