package com.chen.memorizewords.data.floating.repository

import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.WorkInfo
import com.chen.memorizewords.core.common.coroutines.ApplicationScope
import com.chen.memorizewords.core.sprite.DownloadedSpriteSource
import com.chen.memorizewords.core.sprite.SpritePackContractValidator
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.sprite.SpritePackSource
import com.chen.memorizewords.data.floating.local.CharacterPackConditionalWriteResult
import com.chen.memorizewords.data.floating.local.CharacterPackLocalStore
import com.chen.memorizewords.data.floating.remoteapi.CharacterPackRequest
import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadError
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.CharacterPackResolution
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

@Singleton
class CharacterPackRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val request: CharacterPackRequest,
    private val store: CharacterPackLocalStore,
    @param:DownloadedSpriteSource private val downloadedSource: SpritePackSource,
    private val contractValidator: SpritePackContractValidator,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) : CharacterPackRepository {
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val mutationMutex = Mutex()
    private val appliedPackMutex = Mutex()

    init {
        applicationScope.launch(Dispatchers.IO) {
            reconcileQueuedDownloads()
            cleanupOrphanedDirectories()
        }
    }

    override fun observeCatalog(): Flow<List<CharacterPackCatalogItem>> = store.observeCatalog()
    override fun observeInstalled(): Flow<Map<String, InstalledCharacterPack>> = store.observeInstalled()
    override fun observeDownloads(): Flow<Map<String, CharacterPackDownloadState>> =
        store.observeDownloads()

    override suspend fun refreshCatalog(): Result<Unit> {
        return request.getCatalog().fold(
            onSuccess = { items ->
                when {
                    !CharacterPackLocalStore.isValidCompleteCatalog(items) ->
                        Result.failure(IllegalArgumentException("Invalid complete character pack catalog"))
                    !store.replaceCatalog(items) ->
                        Result.failure(CharacterPackPersistenceException())
                    else -> Result.success(Unit)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun resolveAppliedCharacterPack(): Result<CharacterPackResolution> =
        appliedPackMutex.withLock {
            request.resolveAppliedCharacterPack().fold(
                onSuccess = { resolution ->
                    when (resolution) {
                        is CharacterPackResolution.Resolved -> {
                            when {
                                !CharacterPackLocalStore.isValidCatalogItem(resolution.item) ->
                                    Result.failure(
                                        IllegalArgumentException("Invalid resolved character pack")
                                    )

                                !store.replaceResolvedAppliedPack(resolution.item) ->
                                    Result.failure(CharacterPackPersistenceException())

                                else -> Result.success(resolution)
                            }
                        }

                        CharacterPackResolution.SelectionRequired -> {
                            if (store.clearResolvedAppliedPack()) {
                                Result.success(resolution)
                            } else {
                                Result.failure(CharacterPackPersistenceException())
                            }
                        }
                    }
                },
                onFailure = { Result.failure(it) }
            )
        }

    override suspend fun applyCharacterPack(packId: String): Result<Unit> =
        appliedPackMutex.withLock {
            if (!CharacterPackLocalStore.isSafePackId(packId)) {
                return@withLock Result.failure(IllegalArgumentException("Invalid character pack id"))
            }
            request.applyCharacterPack(packId).fold(
                onSuccess = {
                    val appliedPack = store.catalog().firstOrNull { it.packId == packId }
                    val persisted = if (appliedPack != null) {
                        store.replaceResolvedAppliedPack(appliedPack)
                    } else {
                        store.clearResolvedAppliedPack()
                    }
                    if (persisted) Result.success(Unit) else Result.failure(CharacterPackPersistenceException())
                },
                onFailure = { Result.failure(it) }
            )
        }

    override suspend fun startDownload(
        item: CharacterPackCatalogItem,
        selectAfterInstall: Boolean,
        activationRequestId: String?
    ): Result<Unit> = mutationMutex.withLock {
        if (
            !CharacterPackLocalStore.isValidCatalogItem(item) ||
            (activationRequestId != null &&
                !CharacterPackLocalStore.isValidRequestId(activationRequestId))
        ) {
            if (CharacterPackLocalStore.isSafePackId(item.packId)) {
                val persisted = store.putDownload(
                    CharacterPackDownloadState(
                        packId = item.packId,
                        packVersion = item.packVersion,
                        status = CharacterPackDownloadStatus.FAILED,
                        totalBytes = item.packageSizeBytes.coerceAtLeast(0L),
                        errorMessage = "角色下载信息无效，请刷新后重试",
                        errorCode = CharacterPackDownloadError.INVALID_PACKAGE,
                        selectAfterInstall = selectAfterInstall,
                        activationRequestId = activationRequestId
                    )
                )
                if (!persisted) return@withLock Result.failure(CharacterPackPersistenceException())
            }
            return@withLock Result.failure(
                IllegalArgumentException("Invalid character pack catalog item")
            )
        }

        val work = try {
            OneTimeWorkRequestBuilder<CharacterPackDownloadWorker>()
                .setConstraints(CharacterPackWork.downloadConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        CharacterPackWork.KEY_PACK_ID to item.packId,
                        CharacterPackWork.KEY_PACK_VERSION to item.packVersion,
                        CharacterPackWork.KEY_MANIFEST_SCHEMA_VERSION to item.manifestSchemaVersion,
                        CharacterPackWork.KEY_DISPLAY_NAME to item.displayName,
                        CharacterPackWork.KEY_DESCRIPTION to item.description,
                        CharacterPackWork.KEY_PREVIEW_URL to item.previewUrl,
                        CharacterPackWork.KEY_PACKAGE_URL to item.packageUrl,
                        CharacterPackWork.KEY_PACKAGE_SHA256 to item.packageSha256,
                        CharacterPackWork.KEY_PACKAGE_SIZE to item.packageSizeBytes,
                        CharacterPackWork.KEY_SELECT_AFTER_INSTALL to selectAfterInstall,
                        CharacterPackWork.KEY_ACTIVATION_REQUEST_ID to activationRequestId
                    )
                )
                .addTag(CharacterPackWork.tag(item.packId))
                .build()
        } catch (error: Exception) {
            return@withLock Result.failure(error)
        }
        val downloadRequestId = work.id.toString()
        val queuedState = CharacterPackDownloadState(
            packId = item.packId,
            packVersion = item.packVersion,
            downloadRequestId = downloadRequestId,
            status = CharacterPackDownloadStatus.QUEUED,
            totalBytes = item.packageSizeBytes,
            selectAfterInstall = selectAfterInstall,
            activationRequestId = activationRequestId
        )
        return@withLock try {
            withContext(Dispatchers.IO) {
                store.withStateLock {
                    if (!store.putDownload(queuedState)) {
                        throw CharacterPackPersistenceException()
                    }
                    workManager.enqueueUniqueWork(
                        CharacterPackWork.uniqueName(item.packId),
                        ExistingWorkPolicy.REPLACE,
                        work
                    ).awaitCompletionBlocking()
                }
            }
            Result.success(Unit)
        } catch (error: Exception) {
            if (
                error is CharacterPackPersistenceException ||
                    !withContext(Dispatchers.IO) {
                        isQueuedWorkMissing(queuedState)
                    }
            ) {
                return@withLock Result.failure(error)
            }

            store.updateDownloadIfCurrent(
                packId = item.packId,
                downloadRequestId = downloadRequestId,
                updatedState = queuedState.copy(
                    status = CharacterPackDownloadStatus.FAILED,
                    errorMessage = "暂时无法创建下载任务，请重试",
                    errorCode = CharacterPackDownloadError.UNKNOWN
                )
            )
            Result.failure(error)
        }
    }

    private fun isQueuedWorkMissing(queuedState: CharacterPackDownloadState): Boolean =
        store.withStateLock {
            val current = store.download(queuedState.packId)
            if (
                current?.status != CharacterPackDownloadStatus.QUEUED ||
                    current.downloadRequestId != queuedState.downloadRequestId
            ) {
                return@withStateLock false
            }
            val workId = parseWorkId(current.downloadRequestId) ?: return@withStateLock false
            when (val lookup = lookupWorkInfo(workId)) {
                is WorkInfoLookup.Available -> lookup.workInfo == null
                WorkInfoLookup.Unavailable -> false
            }
        }

    private fun reconcileQueuedDownloads() {
        store.downloads().values
            .filter { it.status == CharacterPackDownloadStatus.QUEUED }
            .forEach(::reconcileQueuedDownload)
    }

    private fun reconcileQueuedDownload(snapshot: CharacterPackDownloadState) {
        store.withStateLock {
            val current = store.download(snapshot.packId)
            if (
                current?.status != CharacterPackDownloadStatus.QUEUED ||
                    current.downloadRequestId != snapshot.downloadRequestId
            ) {
                return@withStateLock
            }
            val workId = parseWorkId(current.downloadRequestId)
            if (workId == null) {
                markQueuedDownloadFailed(
                    current,
                    CharacterPackDownloadError.UNKNOWN,
                    "\u4E0B\u8F7D\u4EFB\u52A1\u5DF2\u5931\u6548\uFF0C\u8BF7\u91CD\u65B0\u4E0B\u8F7D"
                )
                return@withStateLock
            }
            when (val lookup = lookupWorkInfo(workId)) {
                is WorkInfoLookup.Available -> {
                    val workInfo = lookup.workInfo
                    when {
                        workInfo == null -> recreateQueuedWork(current, workId)
                        workInfo.state.isActiveDownloadWork() -> Unit
                        else -> markQueuedDownloadFailed(
                            current,
                            CharacterPackDownloadError.UNKNOWN,
                            "\u4E0B\u8F7D\u4EFB\u52A1\u5DF2\u7ED3\u675F\uFF0C\u8BF7\u91CD\u65B0\u4E0B\u8F7D"
                        )
                    }
                }

                WorkInfoLookup.Unavailable -> Unit
            }
        }
    }

    private fun recreateQueuedWork(
        current: CharacterPackDownloadState,
        workId: UUID
    ) {
        val item = store.catalog().firstOrNull {
            it.packId == current.packId && it.packVersion == current.packVersion
        }
        if (item == null) {
            markQueuedDownloadFailed(
                current,
                CharacterPackDownloadError.INVALID_PACKAGE,
                "\u89D2\u8272\u5305\u4FE1\u606F\u5DF2\u8FC7\u671F\uFF0C\u8BF7\u5237\u65B0\u540E\u91CD\u8BD5"
            )
            return
        }
        try {
            workManager.enqueueUniqueWork(
                CharacterPackWork.uniqueName(current.packId),
                ExistingWorkPolicy.KEEP,
                createDownloadWork(
                    item = item,
                    selectAfterInstall = current.selectAfterInstall,
                    activationRequestId = current.activationRequestId,
                    workId = workId
                )
            ).awaitCompletionBlocking()
        } catch (_: Exception) {
            // Keep the queued state. A later startup can retry reconciliation without declaring a
            // transient WorkManager/database failure to be a user-visible download failure.
        }
    }

    private fun createDownloadWork(
        item: CharacterPackCatalogItem,
        selectAfterInstall: Boolean,
        activationRequestId: String?,
        workId: UUID
    ): OneTimeWorkRequest = OneTimeWorkRequestBuilder<CharacterPackDownloadWorker>()
        .setId(workId)
        .setConstraints(CharacterPackWork.downloadConstraints())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .setInputData(
            workDataOf(
                CharacterPackWork.KEY_PACK_ID to item.packId,
                CharacterPackWork.KEY_PACK_VERSION to item.packVersion,
                CharacterPackWork.KEY_MANIFEST_SCHEMA_VERSION to item.manifestSchemaVersion,
                CharacterPackWork.KEY_DISPLAY_NAME to item.displayName,
                CharacterPackWork.KEY_DESCRIPTION to item.description,
                CharacterPackWork.KEY_PREVIEW_URL to item.previewUrl,
                CharacterPackWork.KEY_PACKAGE_URL to item.packageUrl,
                CharacterPackWork.KEY_PACKAGE_SHA256 to item.packageSha256,
                CharacterPackWork.KEY_PACKAGE_SIZE to item.packageSizeBytes,
                CharacterPackWork.KEY_SELECT_AFTER_INSTALL to selectAfterInstall,
                CharacterPackWork.KEY_ACTIVATION_REQUEST_ID to activationRequestId
            )
        )
        .addTag(CharacterPackWork.tag(item.packId))
        .build()

    private fun markQueuedDownloadFailed(
        current: CharacterPackDownloadState,
        errorCode: CharacterPackDownloadError,
        errorMessage: String
    ) {
        store.updateDownloadIfCurrent(
            packId = current.packId,
            downloadRequestId = current.downloadRequestId.orEmpty(),
            updatedState = current.copy(
                status = CharacterPackDownloadStatus.FAILED,
                errorCode = errorCode,
                errorMessage = errorMessage
            )
        )
    }

    private fun parseWorkId(value: String?): UUID? = value?.let { requestId ->
        runCatching { UUID.fromString(requestId) }.getOrNull()
    }

    private fun lookupWorkInfo(workId: UUID): WorkInfoLookup = try {
        WorkInfoLookup.Available(workManager.getWorkInfoById(workId).get())
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        WorkInfoLookup.Unavailable
    } catch (_: Exception) {
        WorkInfoLookup.Unavailable
    }

    private fun WorkInfo.State.isActiveDownloadWork(): Boolean =
        this == WorkInfo.State.ENQUEUED ||
            this == WorkInfo.State.RUNNING ||
            this == WorkInfo.State.BLOCKED

    private sealed class WorkInfoLookup {
        data class Available(val workInfo: WorkInfo?) : WorkInfoLookup()
        object Unavailable : WorkInfoLookup()
    }

    override suspend fun cancelDownload(packId: String) = mutationMutex.withLock {
        if (!CharacterPackLocalStore.isSafePackId(packId)) return@withLock
        invalidateAndCancel(packId)
        withContext(Dispatchers.IO) {
            val cacheRoot = File(context.cacheDir, "character_packs/$packId")
            if (cacheRoot.exists() && !cacheRoot.deleteRecursively()) {
                throw IOException("Unable to remove character pack download files")
            }
        }
    }

    override suspend fun acknowledgeManagementDownloadCompletion(
        packId: String,
        downloadRequestId: String
    ): Boolean = mutationMutex.withLock {
        when (store.removeManagementCompletionIfCurrent(packId, downloadRequestId)) {
            CharacterPackConditionalWriteResult.UPDATED -> true
            CharacterPackConditionalWriteResult.STALE -> false
            CharacterPackConditionalWriteResult.PERSISTENCE_FAILED ->
                throw CharacterPackPersistenceException()
        }
    }

    override suspend fun acknowledgeRuntimeReady(
        packId: String,
        packVersion: Int,
        installedDirectory: String
    ): Boolean = mutationMutex.withLock {
        val pending = store.installed(packId)?.takeIf { installed ->
            installed.packVersion == packVersion &&
                installed.installedDirectory == installedDirectory &&
                installed.pendingRuntimeValidation
        } ?: return@withLock false
        when (
            store.acknowledgeRuntimeReadyIfCurrent(
                packId = packId,
                packVersion = packVersion,
                installedDirectory = installedDirectory
            )
        ) {
            CharacterPackConditionalWriteResult.UPDATED -> {
                pending.lastKnownGoodDirectory?.let { fallbackDirectory ->
                    deleteInstalledDirectoryIfUnreferenced(packId, fallbackDirectory)
                }
                true
            }
            CharacterPackConditionalWriteResult.STALE -> false
            CharacterPackConditionalWriteResult.PERSISTENCE_FAILED ->
                throw CharacterPackPersistenceException()
        }
    }

    override suspend fun rollbackPendingRuntimeValidation(
        packId: String,
        packVersion: Int,
        installedDirectory: String
    ): Boolean = mutationMutex.withLock {
        val pending = store.installed(packId)?.takeIf { installed ->
            installed.packVersion == packVersion &&
                installed.installedDirectory == installedDirectory &&
                installed.pendingRuntimeValidation
        } ?: return@withLock false
        when (
            store.rollbackPendingRuntimeValidationIfCurrent(
                packId = packId,
                packVersion = packVersion,
                installedDirectory = installedDirectory
            )
        ) {
            CharacterPackConditionalWriteResult.UPDATED -> {
                deleteInstalledDirectoryIfUnreferenced(packId, pending.installedDirectory)
                true
            }
            CharacterPackConditionalWriteResult.STALE -> false
            CharacterPackConditionalWriteResult.PERSISTENCE_FAILED ->
                throw CharacterPackPersistenceException()
        }
    }
    override suspend fun deleteInstalled(packId: String) = mutationMutex.withLock {
        if (!CharacterPackLocalStore.isSafePackId(packId)) return@withLock
        invalidateAndCancel(packId)
        if (!store.removePack(packId)) throw CharacterPackPersistenceException()
        withContext(Dispatchers.IO) {
            val packRoot = File(context.filesDir, "character_packs/$packId")
            if (packRoot.exists() && !packRoot.deleteRecursively()) {
                throw IOException("Unable to remove installed character pack")
            }
        }
    }

    override suspend fun getInstalled(packId: String): InstalledCharacterPack? =
        store.installed(packId)

    override suspend fun isInstalledUsable(packId: String): Boolean {
        if (!CharacterPackLocalStore.isSafePackId(packId)) return false
        val usable = try {
            val pack = downloadedSource.load(SpritePackId(packId))
            if (pack == null) {
                false
            } else {
                contractValidator.validate(pack.manifest)
                true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (!usable) {
            store.installed(packId)?.let { removeInvalidInstalledPack(it) }
        }
        return usable
    }

    private suspend fun invalidateAndCancel(packId: String) {
        if (!store.invalidateDownload(packId)) throw CharacterPackPersistenceException()
        workManager.cancelUniqueWork(CharacterPackWork.uniqueName(packId)).awaitCompletion()
    }

    private suspend fun removeInvalidInstalledPack(installed: InstalledCharacterPack) {
        if (
            store.removeInstalledIfCurrent(installed) !=
            CharacterPackConditionalWriteResult.UPDATED
        ) {
            return
        }
        listOfNotNull(
            installed.installedDirectory,
            installed.lastKnownGoodDirectory
        ).distinct().forEach { path ->
            safeInstalledDirectory(installed.packId, path)?.let { directory ->
                withContext(Dispatchers.IO) { directory.deleteRecursively() }
            }
        }
    }

    private suspend fun deleteInstalledDirectoryIfUnreferenced(
        packId: String,
        path: String
    ) {
        val current = store.installed(packId)
        if (
            current?.installedDirectory == path ||
            current?.lastKnownGoodDirectory == path
        ) {
            return
        }
        safeInstalledDirectory(packId, path)?.let { directory ->
            withContext(Dispatchers.IO) { directory.deleteRecursively() }
        }
    }

    private fun safeInstalledDirectory(packId: String, path: String): File? {
        return try {
            val packRoot = File(context.filesDir, "character_packs/$packId").canonicalFile
            File(path).canonicalFile.takeIf { directory ->
                directory.parentFile == packRoot &&
                    CharacterPackLocalStore.isManagedInstalledDirectoryName(directory.name)
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun cleanupOrphanedDirectories() {
        val now = System.currentTimeMillis()
        val installedDirectories = store.installedPacks().values.flatMap { installed ->
            listOfNotNull(
                installed.installedDirectory,
                installed.lastKnownGoodDirectory
            ).mapNotNull { path ->
                safeInstalledDirectory(installed.packId, path)?.absolutePath
            }
        }.toSet()
        val filesRoot = File(context.filesDir, "character_packs")
        filesRoot.listFiles()
            ?.filter { it.isDirectory && CharacterPackLocalStore.isSafePackId(it.name) }
            ?.forEach { packRoot ->
                packRoot.listFiles()?.forEach { candidate ->
                    val isManagedOrphan = candidate.name.startsWith(".install-") ||
                        candidate.name.startsWith(".legacy-install-") ||
                        CharacterPackLocalStore.isManagedInstalledDirectoryName(candidate.name)
                    if (
                        isManagedOrphan &&
                        candidate.absolutePath !in installedDirectories &&
                        isOlderThanGracePeriod(candidate, now)
                    ) {
                        candidate.deleteRecursively()
                    }
                }
            }

        val cacheRoot = File(context.cacheDir, "character_packs")
        cacheRoot.listFiles()
            ?.filter { it.isDirectory && CharacterPackLocalStore.isSafePackId(it.name) }
            ?.forEach { packCache ->
                packCache.listFiles()
                    ?.filter { it.isDirectory && UUID_DIRECTORY.matches(it.name) }
                    ?.filter { isOlderThanGracePeriod(it, now) }
                    ?.forEach(File::deleteRecursively)
                if (packCache.listFiles().isNullOrEmpty()) packCache.delete()
            }
    }

    private fun isOlderThanGracePeriod(directory: File, now: Long): Boolean {
        val newestDirectChild = directory.listFiles()?.maxOfOrNull(File::lastModified) ?: 0L
        val lastModified = maxOf(directory.lastModified(), newestDirectChild)
        return lastModified > 0L && now - lastModified >= ORPHAN_GRACE_PERIOD_MS
    }

    private suspend fun Operation.awaitCompletion() = withContext(Dispatchers.IO) {
        awaitCompletionBlocking()
    }

    private fun Operation.awaitCompletionBlocking() {
        try {
            result.get()
        } catch (error: ExecutionException) {
            throw (error.cause as? Exception ?: error)
        }
    }

    private companion object {
        const val ORPHAN_GRACE_PERIOD_MS = 24L * 60L * 60L * 1_000L
        val UUID_DIRECTORY = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    }
}

internal class CharacterPackPersistenceException : IOException(
    "Unable to persist character pack state"
)

internal object CharacterPackWork {
    const val KEY_PACK_ID = "pack_id"
    const val KEY_PACK_VERSION = "pack_version"
    const val KEY_MANIFEST_SCHEMA_VERSION = "manifest_schema_version"
    const val KEY_DISPLAY_NAME = "display_name"
    const val KEY_DESCRIPTION = "description"
    const val KEY_PREVIEW_URL = "preview_url"
    const val KEY_PACKAGE_URL = "package_url"
    const val KEY_PACKAGE_SHA256 = "package_sha256"
    const val KEY_PACKAGE_SIZE = "package_size"
    const val KEY_SELECT_AFTER_INSTALL = "select_after_install"
    const val KEY_ACTIVATION_REQUEST_ID = "activation_request_id"
    const val KEY_PROGRESS = "progress"

    /**
     * User-initiated downloads may use Wi-Fi or mobile data. A custom request deliberately avoids
     * NET_CAPABILITY_VALIDATED because Android can mark a usable mobile network as only partially
     * connected when its validation endpoint is unavailable.
     */
    fun downloadConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkRequest(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            // API 25-27 cannot use NetworkRequest constraints, so let the HTTP attempt determine
            // connectivity there and rely on the WorkManager backoff for transient failures.
            NetworkType.NOT_REQUIRED
        )
        .build()

    fun uniqueName(packId: String) = "character_pack_download_$packId"
    fun tag(packId: String) = "character_pack_$packId"
}
