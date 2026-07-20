package com.chen.memorizewords.data.floating.local

import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadError
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal interface CharacterPackKeyValueStore {
    fun decodeString(key: String): String?
    fun encodeString(key: String, value: String): Boolean
    fun refreshFromOuterProcess()
    fun <T> withProcessLock(block: () -> T): T
}

private class MmkvCharacterPackKeyValueStore(
    private val mmkv: MMKV
) : CharacterPackKeyValueStore {
    private val lockDepth = ThreadLocal<Int>()

    override fun decodeString(key: String): String? = mmkv.decodeString(key)

    override fun encodeString(key: String, value: String): Boolean = mmkv.encode(key, value)

    override fun refreshFromOuterProcess() {
        mmkv.checkContentChangedByOuterProcess()
    }

    override fun <T> withProcessLock(block: () -> T): T {
        val depth = lockDepth.get() ?: 0
        if (depth > 0) return block()
        mmkv.lock()
        lockDepth.set(1)
        return try {
            block()
        } finally {
            lockDepth.remove()
            mmkv.unlock()
        }
    }
}

internal enum class CharacterPackConditionalWriteResult {
    UPDATED,
    STALE,
    PERSISTENCE_FAILED
}

@Singleton
class CharacterPackLocalStore internal constructor(
    private val keyValueStore: CharacterPackKeyValueStore,
    private val gson: Gson,
    private val observerRefreshIntervalMs: Long = OUTER_PROCESS_REFRESH_INTERVAL_MS
) {
    @Inject
    constructor(mmkv: MMKV, gson: Gson) : this(
        keyValueStore = MmkvCharacterPackKeyValueStore(mmkv),
        gson = gson
    )

    private val stateType = object : TypeToken<PersistedCharacterPackState>() {}.type
    private val catalogType = object : TypeToken<List<CharacterPackCatalogItem>>() {}.type
    private val installedType = object : TypeToken<Map<String, InstalledCharacterPack>>() {}.type
    private val downloadsType = object : TypeToken<Map<String, CharacterPackDownloadState>>() {}.type

    private val initialState = keyValueStore.withProcessLock {
        refreshFromOuterProcess()
        readStateWithSource()
    }
    private var persistedState = initialState.state
    private val catalogState = MutableStateFlow(
        catalogForObservation(persistedState.catalog, persistedState.resolvedAppliedPack)
    )
    private val installedState = MutableStateFlow(persistedState.installed)
    private val downloadsState = MutableStateFlow(persistedState.downloads)

    init {
        if (initialState.shouldMigrate) {
            // A single v2 payload makes future reads atomic across catalog, installation and
            // download state. Re-read while holding the process lock: another process may have
            // completed the migration after this instance performed its initial read, and that
            // newer v2 state must never be overwritten with an older legacy snapshot.
            keyValueStore.withProcessLock {
                refreshFromOuterProcess()
                val latest = readStateWithSource()
                if (latest.shouldMigrate) {
                    // If persistence fails, keep serving the normalized legacy data and allow the
                    // next successful mutation to complete the migration.
                    if (!persist(latest.state)) publish(latest.state)
                } else {
                    publish(latest.state)
                }
            }
        }
    }

    fun observeCatalog(): Flow<List<CharacterPackCatalogItem>> = observeState(catalogState)
    fun observeInstalled(): Flow<Map<String, InstalledCharacterPack>> = observeState(installedState)
    fun observeDownloads(): Flow<Map<String, CharacterPackDownloadState>> =
        observeState(downloadsState)

    /**
     * Runs a compound read/write under both the in-process monitor and MMKV's process lock.
     * Keeping that order consistent prevents a worker process and the UI process from observing
     * two different download states while a WorkManager request is being scheduled.
     */
    @Synchronized
    internal fun <T> withStateLock(block: () -> T): T = keyValueStore.withProcessLock {
        refreshFromDisk()
        block()
    }

    @Synchronized
    fun catalog(): List<CharacterPackCatalogItem> = keyValueStore.withProcessLock {
        refreshFromDisk()
        catalogForObservation(persistedState.catalog, persistedState.resolvedAppliedPack)
    }

    @Synchronized
    fun installed(packId: String): InstalledCharacterPack? = keyValueStore.withProcessLock {
        refreshFromDisk()
        persistedState.installed[packId]
    }

    @Synchronized
    fun installedPacks(): Map<String, InstalledCharacterPack> = keyValueStore.withProcessLock {
        refreshFromDisk()
        persistedState.installed
    }

    @Synchronized
    fun download(packId: String): CharacterPackDownloadState? = keyValueStore.withProcessLock {
        refreshFromDisk()
        persistedState.downloads[packId]
    }

    @Synchronized
    internal fun downloads(): Map<String, CharacterPackDownloadState> = keyValueStore.withProcessLock {
        refreshFromDisk()
        persistedState.downloads
    }

    @Synchronized
    fun replaceCatalog(items: List<CharacterPackCatalogItem>): Boolean =
        keyValueStore.withProcessLock {
        if (!isValidCompleteCatalog(items)) return@withProcessLock false
        refreshFromDisk()
        val catalog = normalizeCatalog(items)
        val resolvedAppliedPack = persistedState.resolvedAppliedPack?.let { cached ->
            // The catalog is newer than a prior resolve response when it contains the same pack.
            // Keep a resolve-only pack only when it is absent from this complete directory.
            catalog.firstOrNull { it.packId == cached.packId } ?: cached
        }
        persist(
            persistedState.copy(
                catalog = catalog,
                resolvedAppliedPack = resolvedAppliedPack
            )
        )
    }

    @Synchronized
    fun replaceResolvedAppliedPack(item: CharacterPackCatalogItem): Boolean =
        keyValueStore.withProcessLock {
        if (!isValidCatalogItem(item)) return@withProcessLock false
        refreshFromDisk()
        persist(persistedState.copy(resolvedAppliedPack = item))
    }

    @Synchronized
    fun clearResolvedAppliedPack(): Boolean = keyValueStore.withProcessLock {
        refreshFromDisk()
        if (persistedState.resolvedAppliedPack == null) return@withProcessLock true
        persist(persistedState.copy(resolvedAppliedPack = null))
    }

    @Synchronized
    fun putInstalled(item: InstalledCharacterPack): Boolean = keyValueStore.withProcessLock {
        if (!isValidInstalledItem(item)) return@withProcessLock false
        refreshFromDisk()
        val updated = persistedState.installed.toMutableMap().apply {
            put(item.packId, item)
        }.toMap()
        persist(persistedState.copy(installed = updated))
    }

    @Synchronized
    fun removeInstalled(packId: String): Boolean = keyValueStore.withProcessLock {
        if (!isSafePackId(packId)) return@withProcessLock false
        refreshFromDisk()
        val updated = persistedState.installed.toMutableMap().apply { remove(packId) }.toMap()
        persist(persistedState.copy(installed = updated))
    }

    @Synchronized
    internal fun removeInstalledIfCurrent(
        expected: InstalledCharacterPack
    ): CharacterPackConditionalWriteResult = keyValueStore.withProcessLock {
        if (!isValidInstalledItem(expected)) {
            return@withProcessLock CharacterPackConditionalWriteResult.STALE
        }
        refreshFromDisk()
        val current = persistedState.installed[expected.packId]
        if (
            current == null ||
            current.packVersion != expected.packVersion ||
            current.installedDirectory != expected.installedDirectory
        ) {
            return@withProcessLock CharacterPackConditionalWriteResult.STALE
        }
        val updated = persistedState.installed.toMutableMap().apply {
            remove(expected.packId)
        }.toMap()
        if (persist(persistedState.copy(installed = updated))) {
            CharacterPackConditionalWriteResult.UPDATED
        } else {
            CharacterPackConditionalWriteResult.PERSISTENCE_FAILED
        }
    }

    @Synchronized
    fun putDownload(item: CharacterPackDownloadState): Boolean = keyValueStore.withProcessLock {
        if (!isSafePackId(item.packId)) return@withProcessLock false
        refreshFromDisk()
        val updated = persistedState.downloads.toMutableMap().apply {
            put(item.packId, normalizeDownload(item))
        }.toMap()
        persist(persistedState.copy(downloads = updated))
    }

    @Synchronized
    internal fun updateDownloadIfCurrent(
        packId: String,
        downloadRequestId: String,
        updatedState: CharacterPackDownloadState
    ): CharacterPackConditionalWriteResult = keyValueStore.withProcessLock {
        refreshFromDisk()
        val current = persistedState.downloads[packId]
        val normalizedState = normalizeDownload(updatedState)
        if (
            current?.downloadRequestId != downloadRequestId ||
            normalizedState.packId != packId ||
            normalizedState.downloadRequestId != downloadRequestId ||
            current.status.isTerminalDownloadStatus() &&
                normalizedState.status != current.status
        ) {
            return@withProcessLock CharacterPackConditionalWriteResult.STALE
        }
        val updated = persistedState.downloads.toMutableMap().apply {
            put(packId, normalizedState)
        }.toMap()
        if (persist(persistedState.copy(downloads = updated))) {
            CharacterPackConditionalWriteResult.UPDATED
        } else {
            CharacterPackConditionalWriteResult.PERSISTENCE_FAILED
        }
    }

    @Synchronized
    internal fun commitInstallationIfCurrent(
        packId: String,
        downloadRequestId: String,
        installed: InstalledCharacterPack,
        completedDownload: CharacterPackDownloadState
    ): CharacterPackConditionalWriteResult = keyValueStore.withProcessLock {
        refreshFromDisk()
        val current = persistedState.downloads[packId]
        val normalizedCompleted = normalizeDownload(completedDownload)
        if (
            current?.downloadRequestId != downloadRequestId ||
            installed.packId != packId ||
            !isValidInstalledItem(installed) ||
            normalizedCompleted.packId != packId ||
            normalizedCompleted.downloadRequestId != downloadRequestId ||
            normalizedCompleted.status != CharacterPackDownloadStatus.COMPLETED
        ) {
            return@withProcessLock CharacterPackConditionalWriteResult.STALE
        }
        val installedPacks = persistedState.installed.toMutableMap().apply {
            put(packId, installed)
        }.toMap()
        val downloads = persistedState.downloads.toMutableMap().apply {
            put(packId, normalizedCompleted)
        }.toMap()
        if (persist(persistedState.copy(installed = installedPacks, downloads = downloads))) {
            CharacterPackConditionalWriteResult.UPDATED
        } else {
            CharacterPackConditionalWriteResult.PERSISTENCE_FAILED
        }
    }

    @Synchronized
    internal fun removeManagementCompletionIfCurrent(
        packId: String,
        downloadRequestId: String
    ): CharacterPackConditionalWriteResult = keyValueStore.withProcessLock {
        if (!isSafePackId(packId) || !isValidRequestId(downloadRequestId)) {
            return@withProcessLock CharacterPackConditionalWriteResult.STALE
        }
        refreshFromDisk()
        val current = persistedState.downloads[packId]
        if (
            current?.downloadRequestId != downloadRequestId ||
            current.status != CharacterPackDownloadStatus.COMPLETED ||
            current.selectAfterInstall ||
            current.activationRequestId != null
        ) {
            return@withProcessLock CharacterPackConditionalWriteResult.STALE
        }
        val downloads = persistedState.downloads.toMutableMap().apply { remove(packId) }.toMap()
        if (persist(persistedState.copy(downloads = downloads))) {
            CharacterPackConditionalWriteResult.UPDATED
        } else {
            CharacterPackConditionalWriteResult.PERSISTENCE_FAILED
        }
    }

    @Synchronized
    fun invalidateDownload(packId: String): Boolean = keyValueStore.withProcessLock {
        if (!isSafePackId(packId)) return@withProcessLock false
        refreshFromDisk()
        val previous = persistedState.downloads[packId]
        val cancelled = (previous ?: CharacterPackDownloadState(packId = packId)).copy(
            downloadRequestId = null,
            status = CharacterPackDownloadStatus.CANCELLED,
            downloadedBytes = 0L,
            progress = 0,
            errorMessage = null,
            errorCode = null,
            selectAfterInstall = false,
            activationRequestId = null
        )
        val downloads = persistedState.downloads.toMutableMap().apply {
            put(packId, cancelled)
        }.toMap()
        persist(persistedState.copy(downloads = downloads))
    }

    @Synchronized
    fun removeDownload(packId: String): Boolean = keyValueStore.withProcessLock {
        if (!isSafePackId(packId)) return@withProcessLock false
        refreshFromDisk()
        val updated = persistedState.downloads.toMutableMap().apply { remove(packId) }.toMap()
        persist(persistedState.copy(downloads = updated))
    }

    @Synchronized
    fun removePack(packId: String): Boolean = keyValueStore.withProcessLock {
        if (!isSafePackId(packId)) return@withProcessLock false
        refreshFromDisk()
        val installed = persistedState.installed.toMutableMap().apply { remove(packId) }.toMap()
        val downloads = persistedState.downloads.toMutableMap().apply { remove(packId) }.toMap()
        persist(persistedState.copy(installed = installed, downloads = downloads))
    }

    private fun refreshFromDisk() {
        refreshFromOuterProcess()
        val latest = readState()
        if (latest != persistedState) publish(latest)
    }

    private fun refreshFromOuterProcess() {
        try {
            keyValueStore.refreshFromOuterProcess()
        } catch (_: Exception) {
            // A transient MMKV refresh failure should not make the floating process crash. The
            // next read or observer tick will retry and the persisted state remains authoritative.
        }
    }

    private fun <T> observeState(state: StateFlow<T>): Flow<T> = flow {
        refreshObservableState()
        emitAll(
            merge(
                state,
                flow {
                    while (currentCoroutineContext().isActive) {
                        delay(observerRefreshIntervalMs.coerceAtLeast(1L))
                        refreshObservableState()
                        emit(state.value)
                    }
                }
            )
        )
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    @Synchronized
    private fun refreshObservableState() {
        keyValueStore.withProcessLock { refreshFromDisk() }
    }

    private fun persist(state: CharacterPackState): Boolean {
        val json = try {
            gson.toJson(
                PersistedCharacterPackState(
                    catalog = state.catalog,
                    resolvedAppliedPack = state.resolvedAppliedPack,
                    installed = state.installed,
                    downloads = state.downloads
                )
            )
        } catch (_: Exception) {
            return false
        }
        val encoded = try {
            keyValueStore.encodeString(KEY_STATE, json)
        } catch (_: Exception) {
            false
        }
        if (!encoded) return false
        publish(state)
        return true
    }

    private fun publish(state: CharacterPackState) {
        persistedState = state
        catalogState.value = catalogForObservation(
            state.catalog,
            state.resolvedAppliedPack
        )
        installedState.value = state.installed
        downloadsState.value = state.downloads
    }

    private fun readState(): CharacterPackState = readStateWithSource().state

    private fun readStateWithSource(): StateReadResult {
        val encodedState = decodeStringSafely(KEY_STATE)
        if (encodedState != null) {
            val state = try {
                val decoded = gson.fromJson<PersistedCharacterPackState>(encodedState, stateType)
                normalizeState(decoded)
            } catch (_: Exception) {
                CharacterPackState()
            }
            return StateReadResult(state = state, shouldMigrate = false)
        }
        return readLegacyState()
    }

    private fun readLegacyState(): StateReadResult {
        val encodedCatalog = decodeStringSafely(KEY_CATALOG)
        val encodedInstalled = decodeStringSafely(KEY_INSTALLED)
        val encodedDownloads = decodeStringSafely(KEY_DOWNLOADS)
        val catalog = decodeLegacy<List<CharacterPackCatalogItem>>(
            encodedCatalog,
            catalogType
        ).orEmpty()
        val installed = decodeLegacy<Map<String, InstalledCharacterPack>>(
            encodedInstalled,
            installedType
        ).orEmpty()
        val downloads = decodeLegacy<Map<String, CharacterPackDownloadState>>(
            encodedDownloads,
            downloadsType
        ).orEmpty()
        return StateReadResult(
            state = normalizeState(
                PersistedCharacterPackState(
                    catalog = catalog,
                    installed = installed,
                    downloads = downloads
                )
            ),
            shouldMigrate = encodedCatalog != null ||
                encodedInstalled != null ||
                encodedDownloads != null
        )
    }

    private fun decodeStringSafely(key: String): String? {
        return try {
            keyValueStore.decodeString(key)
        } catch (_: Exception) {
            null
        }
    }

    private fun <T> decodeLegacy(encoded: String?, type: java.lang.reflect.Type): T? {
        return try {
            encoded ?: return null
            gson.fromJson<T>(encoded, type)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeState(state: PersistedCharacterPackState?): CharacterPackState {
        return CharacterPackState(
            catalog = normalizeCatalog(state?.catalog.orEmpty()),
            resolvedAppliedPack = state?.resolvedAppliedPack?.takeIf(::isValidCatalogItem),
            installed = state?.installed.orEmpty().filter { (packId, installed) ->
                installed.packId == packId && isValidInstalledItem(installed)
            },
            downloads = state?.downloads.orEmpty().mapNotNull { (packId, download) ->
                if (!isSafePackId(packId) || download.packId != packId) {
                    null
                } else {
                    packId to normalizeDownload(download)
                }
            }.toMap()
        )
    }

    private fun normalizeDownload(download: CharacterPackDownloadState): CharacterPackDownloadState {
        val requestId = download.downloadRequestId?.takeIf(::isValidRequestId)
        val activationRequestId = download.activationRequestId?.takeIf(::isValidRequestId)
        val totalBytes = download.totalBytes.coerceIn(0L, MAX_PACKAGE_BYTES)
        val active = download.status == CharacterPackDownloadStatus.QUEUED ||
            download.status == CharacterPackDownloadStatus.DOWNLOADING ||
            download.status == CharacterPackDownloadStatus.INSTALLING
        if (
            active && (
                requestId == null ||
                    download.packVersion <= 0 ||
                    totalBytes <= 0L ||
                    (download.activationRequestId != null && activationRequestId == null)
                )
        ) {
            return download.copy(
                downloadRequestId = null,
                status = CharacterPackDownloadStatus.FAILED,
                downloadedBytes = 0L,
                totalBytes = totalBytes,
                progress = 0,
                errorMessage = "下载任务已失效，请重新下载",
                errorCode = CharacterPackDownloadError.UNKNOWN,
                selectAfterInstall = false,
                activationRequestId = null
            )
        }
        val downloadedBytes = download.downloadedBytes.coerceIn(0L, totalBytes)
        return download.copy(
            downloadRequestId = requestId,
            activationRequestId = activationRequestId,
            packVersion = download.packVersion.coerceAtLeast(0),
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            progress = download.progress.coerceIn(0, 100)
        )
    }

    private fun CharacterPackDownloadStatus.isTerminalDownloadStatus(): Boolean =
        this == CharacterPackDownloadStatus.COMPLETED ||
            this == CharacterPackDownloadStatus.FAILED ||
            this == CharacterPackDownloadStatus.CANCELLED

    private data class CharacterPackState(
        val catalog: List<CharacterPackCatalogItem> = emptyList(),
        val resolvedAppliedPack: CharacterPackCatalogItem? = null,
        val installed: Map<String, InstalledCharacterPack> = emptyMap(),
        val downloads: Map<String, CharacterPackDownloadState> = emptyMap()
    )

    private data class StateReadResult(
        val state: CharacterPackState,
        val shouldMigrate: Boolean
    )

    private data class PersistedCharacterPackState(
        val catalog: List<CharacterPackCatalogItem>? = null,
        val resolvedAppliedPack: CharacterPackCatalogItem? = null,
        val installed: Map<String, InstalledCharacterPack>? = null,
        val downloads: Map<String, CharacterPackDownloadState>? = null
    )

    companion object {
        internal const val MAX_PACKAGE_BYTES = 25L * 1024L * 1024L
        private const val OUTER_PROCESS_REFRESH_INTERVAL_MS = 750L
        internal const val MAX_MANIFEST_BYTES = 128L * 1024L
        internal const val MAX_ATLAS_BYTES = 60L * 1024L * 1024L
        private const val SUPPORTED_MANIFEST_SCHEMA_VERSION = 1
        private const val MAX_DISPLAY_NAME_CHARS = 80
        private const val MAX_DESCRIPTION_CHARS = 500
        private const val MAX_URL_CHARS = 2_048
        private const val MAX_DOWNLOAD_METADATA_UTF8_BYTES = 6 * 1_024
        internal const val MAX_CATALOG_ITEMS = 200
        private val SHA_256 = Regex("[a-fA-F0-9]{64}")
        private const val UUID_PATTERN =
            "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"
        private val INSTALLED_DIRECTORY = Regex("([1-9][0-9]*)-$UUID_PATTERN")

        private const val KEY_STATE = "character_pack_state_v2"
        private const val KEY_CATALOG = "character_pack_catalog_v1"
        private const val KEY_INSTALLED = "character_pack_installed_v1"
        private const val KEY_DOWNLOADS = "character_pack_downloads_v1"

        fun isSafePackId(value: String): Boolean =
            value.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}"))

        internal fun isValidRequestId(value: String): Boolean = runCatching {
            UUID.fromString(value).toString().equals(value, ignoreCase = true)
        }.getOrDefault(false)

        internal fun isManagedInstalledDirectoryName(value: String): Boolean =
            INSTALLED_DIRECTORY.matches(value)

        internal fun isInstalledDirectoryForVersion(value: String, packVersion: Int): Boolean {
            if (packVersion <= 0) return false
            val match = INSTALLED_DIRECTORY.matchEntire(value) ?: return false
            return match.groupValues[1].toIntOrNull() == packVersion
        }

        fun isValidCatalogItem(item: CharacterPackCatalogItem): Boolean {
            val previewUrl = item.previewUrl.toHttpUrlOrNull()
            val packageUrl = item.packageUrl.toHttpUrlOrNull()
            return isSafePackId(item.packId) &&
                item.packVersion > 0 &&
                item.displayName.isNotBlank() &&
                item.displayName.length <= MAX_DISPLAY_NAME_CHARS &&
                (item.description?.length ?: 0) <= MAX_DESCRIPTION_CHARS &&
                item.previewUrl.length <= MAX_URL_CHARS &&
                item.packageUrl.length <= MAX_URL_CHARS &&
                previewUrl?.isHttps == true &&
                previewUrl.encodedUsername.isEmpty() &&
                previewUrl.encodedPassword.isEmpty() &&
                previewUrl.fragment == null &&
                packageUrl?.isHttps == true &&
                packageUrl.encodedUsername.isEmpty() &&
                packageUrl.encodedPassword.isEmpty() &&
                packageUrl.fragment == null &&
                item.packageSha256.matches(SHA_256) &&
                item.packageSizeBytes in 1..MAX_PACKAGE_BYTES &&
                item.manifestSchemaVersion == SUPPORTED_MANIFEST_SCHEMA_VERSION &&
                item.updatedAtMs >= 0L &&
                downloadMetadataUtf8Bytes(item) <= MAX_DOWNLOAD_METADATA_UTF8_BYTES
        }

        private fun downloadMetadataUtf8Bytes(item: CharacterPackCatalogItem): Int = listOf(
            item.packId,
            item.displayName,
            item.description.orEmpty(),
            item.previewUrl,
            item.packageUrl,
            item.packageSha256
        ).sumOf { it.toByteArray(Charsets.UTF_8).size }

        private fun isValidInstalledItem(item: InstalledCharacterPack): Boolean =
            isSafePackId(item.packId) &&
                item.packVersion > 0 &&
                item.displayName.isNotBlank() &&
                item.displayName.length <= MAX_DISPLAY_NAME_CHARS &&
                (item.description?.length ?: 0) <= MAX_DESCRIPTION_CHARS &&
                (item.previewUrl?.length ?: 0) <= MAX_URL_CHARS &&
                item.installedDirectory.isNotBlank() &&
                item.installedAtMs >= 0L

        /** Validates a complete server directory, not an individual resolve response. */
        internal fun isValidCompleteCatalog(items: List<CharacterPackCatalogItem>): Boolean =
            items.size <= MAX_CATALOG_ITEMS &&
                items.map(CharacterPackCatalogItem::packId).toSet().size == items.size &&
                items.all(::isValidCatalogItem) &&
                items.count(CharacterPackCatalogItem::isDefault) == 1

        private fun catalogForObservation(
            catalog: List<CharacterPackCatalogItem>,
            resolvedAppliedPack: CharacterPackCatalogItem?
        ): List<CharacterPackCatalogItem> {
            val resolved = resolvedAppliedPack ?: return catalog
            val merged = (catalog.filterNot { it.packId == resolved.packId } + resolved)
                .map { item ->
                    if (resolved.isDefault && item.packId != resolved.packId && item.isDefault) {
                        item.copy(isDefault = false)
                    } else {
                        item
                    }
                }
            return merged.sortedWith(
                compareBy<CharacterPackCatalogItem> { it.sortOrder }.thenBy { it.packId }
            )
        }

        private fun normalizeCatalog(
            items: List<CharacterPackCatalogItem>
        ): List<CharacterPackCatalogItem> = if (isValidCompleteCatalog(items)) {
            items.sortedWith(compareBy<CharacterPackCatalogItem> { it.sortOrder }.thenBy { it.packId })
        } else {
            emptyList()
        }
    }
}
