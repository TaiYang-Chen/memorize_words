package com.chen.memorizewords.data.floating.local

import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import com.google.gson.Gson
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CharacterPackLocalStoreTest {
    private val gson = Gson()

    @Test
    fun `legacy migration does not overwrite v2 state written by another process`() {
        val legacyPack = installed("legacy_pet")
        val newerPack = installed("newer_pet")
        val keyValueStore = FakeCharacterPackKeyValueStore(
            mutableMapOf(KEY_INSTALLED_LEGACY to gson.toJson(mapOf(legacyPack.packId to legacyPack)))
        ).apply {
            beforeLock = { lockNumber ->
                if (lockNumber == 2) {
                    values[KEY_STATE] = persistedStateJson(installed = mapOf(newerPack.packId to newerPack))
                }
            }
        }

        val store = CharacterPackLocalStore(keyValueStore, gson)

        assertEquals(setOf(newerPack.packId), store.installedPacks().keys)
        assertEquals(newerPack, store.installed(newerPack.packId))
        assertNull(store.installed(legacyPack.packId))
    }

    @Test
    fun `read modify write merges changes made by another store instance`() {
        val keyValueStore = FakeCharacterPackKeyValueStore()
        val firstProcess = CharacterPackLocalStore(keyValueStore, gson)
        val secondProcess = CharacterPackLocalStore(keyValueStore, gson)
        val installed = installed("green_pet")
        val download = queuedDownload("blue_pet")

        assertTrue(firstProcess.putInstalled(installed))
        assertTrue(secondProcess.putDownload(download))

        assertEquals(installed, firstProcess.installed(installed.packId))
        assertEquals(download, firstProcess.download(download.packId))
        assertEquals(installed, secondProcess.installed(installed.packId))
    }

    @Test
    fun `observer receives state written by another process`() = runBlocking {
        val keyValueStore = FakeCharacterPackKeyValueStore()
        val observingProcess = CharacterPackLocalStore(
            keyValueStore,
            gson,
            observerRefreshIntervalMs = 1L
        )
        val writingProcess = CharacterPackLocalStore(keyValueStore, gson)
        val installed = installed("green_pet")
        val initialRefresh = CompletableDeferred<Unit>()
        keyValueStore.onRefreshFromOuterProcess = { initialRefresh.complete(Unit) }
        val observed = async {
            withTimeout(1_000L) {
                observingProcess.observeInstalled().first { it[installed.packId] == installed }
            }
        }

        initialRefresh.await()
        assertTrue(writingProcess.putInstalled(installed))
        assertEquals(installed, observed.await()[installed.packId])
    }

    @Test
    fun `resolved applied pack is persisted and merged into the observable catalog`() {
        val keyValueStore = FakeCharacterPackKeyValueStore()
        val store = CharacterPackLocalStore(keyValueStore, gson)
        val default = catalogItem(packId = "blue_pet", isDefault = true)
        val resolved = catalogItem(packId = "purple_pet", isDefault = false).copy(packVersion = 2)

        assertTrue(store.replaceCatalog(listOf(default)))
        assertTrue(store.replaceResolvedAppliedPack(resolved))

        assertEquals(resolved, store.catalog().first { it.packId == resolved.packId })
        assertEquals(resolved, runBlocking { store.observeCatalog().first().first { it.packId == resolved.packId } })

        val reloaded = CharacterPackLocalStore(keyValueStore, gson)
        assertEquals(resolved, reloaded.catalog().first { it.packId == resolved.packId })

        assertTrue(store.clearResolvedAppliedPack())
        assertEquals(listOf(default), store.catalog())
    }

    @Test
    fun `remove pack clears installed and download state across process instances`() {
        val keyValueStore = FakeCharacterPackKeyValueStore()
        val writingProcess = CharacterPackLocalStore(keyValueStore, gson)
        val deletingProcess = CharacterPackLocalStore(keyValueStore, gson)
        val installed = installed("green_pet")
        val download = queuedDownload("green_pet")

        assertTrue(writingProcess.putInstalled(installed))
        assertTrue(writingProcess.putDownload(download))
        assertTrue(deletingProcess.removePack("green_pet"))

        assertNull(writingProcess.installed("green_pet"))
        assertNull(writingProcess.download("green_pet"))
        assertNull(deletingProcess.installed("green_pet"))
        assertNull(deletingProcess.download("green_pet"))

        val restartedProcess = CharacterPackLocalStore(keyValueStore, gson)
        assertNull(restartedProcess.installed("green_pet"))
        assertNull(restartedProcess.download("green_pet"))
    }

    @Test
    fun `catalog replacement refreshes a matching resolved applied pack`() {
        val keyValueStore = FakeCharacterPackKeyValueStore()
        val store = CharacterPackLocalStore(keyValueStore, gson)
        val default = catalogItem(packId = "blue_pet", isDefault = true)
        val staleResolved = catalogItem(packId = "purple_pet", isDefault = false)
        val refreshedResolved = staleResolved.copy(
            packVersion = staleResolved.packVersion + 1,
            displayName = "Updated purple pet",
            updatedAtMs = staleResolved.updatedAtMs + 1
        )

        assertTrue(store.replaceResolvedAppliedPack(staleResolved))
        assertTrue(store.replaceCatalog(listOf(default, refreshedResolved)))

        assertEquals(
            refreshedResolved,
            store.catalog().first { it.packId == refreshedResolved.packId }
        )
        assertEquals(
            refreshedResolved,
            CharacterPackLocalStore(keyValueStore, gson)
                .catalog()
                .first { it.packId == refreshedResolved.packId }
        )
    }

    @Test
    fun `failed persistence does not publish an uncommitted state`() {
        val keyValueStore = FakeCharacterPackKeyValueStore()
        val store = CharacterPackLocalStore(keyValueStore, gson)
        keyValueStore.failWrites = true

        assertFalse(store.replaceCatalog(listOf(catalogItem())))
        assertFalse(store.putInstalled(installed("green_pet")))
        assertFalse(store.putDownload(queuedDownload("green_pet")))

        assertTrue(runBlocking { store.observeCatalog().first().isEmpty() })
        assertTrue(runBlocking { store.observeInstalled().first().isEmpty() })
        assertTrue(runBlocking { store.observeDownloads().first().isEmpty() })
    }

    @Test
    fun `stale worker cannot update or commit over a newer request`() {
        val store = CharacterPackLocalStore(FakeCharacterPackKeyValueStore(), gson)
        val oldRequest = UUID.randomUUID().toString()
        val newRequest = UUID.randomUUID().toString()
        assertTrue(store.putDownload(queuedDownload("green_pet", oldRequest)))
        assertTrue(store.putDownload(queuedDownload("green_pet", newRequest)))

        val staleUpdate = store.updateDownloadIfCurrent(
            packId = "green_pet",
            downloadRequestId = oldRequest,
            updatedState = queuedDownload("green_pet", oldRequest).copy(
                status = CharacterPackDownloadStatus.DOWNLOADING,
                progress = 50,
                downloadedBytes = 500L
            )
        )
        val staleCommit = store.commitInstallationIfCurrent(
            packId = "green_pet",
            downloadRequestId = oldRequest,
            installed = installed("green_pet"),
            completedDownload = queuedDownload("green_pet", oldRequest).copy(
                status = CharacterPackDownloadStatus.COMPLETED,
                progress = 100,
                downloadedBytes = 1_000L
            )
        )

        assertEquals(CharacterPackConditionalWriteResult.STALE, staleUpdate)
        assertEquals(CharacterPackConditionalWriteResult.STALE, staleCommit)
        assertEquals(newRequest, store.download("green_pet")?.downloadRequestId)
        assertNull(store.installed("green_pet"))
    }

    @Test
    fun `completed worker generation cannot regress back to an active state`() {
        val store = CharacterPackLocalStore(FakeCharacterPackKeyValueStore(), gson)
        val requestId = UUID.randomUUID().toString()
        val completed = queuedDownload("green_pet", requestId).copy(
            status = CharacterPackDownloadStatus.COMPLETED,
            progress = 100,
            downloadedBytes = 1_000L
        )
        assertTrue(store.putDownload(completed))

        val result = store.updateDownloadIfCurrent(
            packId = "green_pet",
            downloadRequestId = requestId,
            updatedState = completed.copy(
                status = CharacterPackDownloadStatus.DOWNLOADING,
                progress = 0,
                downloadedBytes = 0L
            )
        )

        assertEquals(CharacterPackConditionalWriteResult.STALE, result)
        assertEquals(completed, store.download("green_pet"))
    }

    @Test
    fun `completed download acknowledgement is atomic and consumed only once`() {
        val store = CharacterPackLocalStore(FakeCharacterPackKeyValueStore(), gson)
        val completedRequest = UUID.randomUUID().toString()
        val replacementRequest = UUID.randomUUID().toString()
        val completed = queuedDownload("green_pet", completedRequest).copy(
            status = CharacterPackDownloadStatus.COMPLETED,
            progress = 100,
            downloadedBytes = 1_000L
        )
        assertTrue(store.putDownload(completed))

        assertEquals(
            CharacterPackConditionalWriteResult.UPDATED,
            store.removeManagementCompletionIfCurrent("green_pet", completedRequest)
        )
        assertNull(store.download("green_pet"))
        assertEquals(
            CharacterPackConditionalWriteResult.STALE,
            store.removeManagementCompletionIfCurrent("green_pet", completedRequest)
        )

        assertTrue(store.putDownload(queuedDownload("green_pet", replacementRequest)))
        assertEquals(
            CharacterPackConditionalWriteResult.STALE,
            store.removeManagementCompletionIfCurrent("green_pet", completedRequest)
        )
        assertEquals(replacementRequest, store.download("green_pet")?.downloadRequestId)
    }

    @Test
    fun `two store instances cannot both consume the same management completion`() {
        val keyValueStore = FakeCharacterPackKeyValueStore()
        val firstProcess = CharacterPackLocalStore(keyValueStore, gson)
        val secondProcess = CharacterPackLocalStore(keyValueStore, gson)
        val requestId = UUID.randomUUID().toString()
        assertTrue(
            firstProcess.putDownload(
                queuedDownload("green_pet", requestId).copy(
                    status = CharacterPackDownloadStatus.COMPLETED,
                    progress = 100,
                    downloadedBytes = 1_000L
                )
            )
        )

        assertEquals(
            CharacterPackConditionalWriteResult.UPDATED,
            firstProcess.removeManagementCompletionIfCurrent("green_pet", requestId)
        )
        assertEquals(
            CharacterPackConditionalWriteResult.STALE,
            secondProcess.removeManagementCompletionIfCurrent("green_pet", requestId)
        )
        assertNull(secondProcess.download("green_pet"))
    }

    @Test
    fun `management acknowledgement cannot consume activation completion`() {
        val store = CharacterPackLocalStore(FakeCharacterPackKeyValueStore(), gson)
        val requestId = UUID.randomUUID().toString()
        val activationRequestId = UUID.randomUUID().toString()
        val activationCompletion = queuedDownload("green_pet", requestId).copy(
            status = CharacterPackDownloadStatus.COMPLETED,
            progress = 100,
            downloadedBytes = 1_000L,
            activationRequestId = activationRequestId
        )
        assertTrue(store.putDownload(activationCompletion))

        assertEquals(
            CharacterPackConditionalWriteResult.STALE,
            store.removeManagementCompletionIfCurrent("green_pet", requestId)
        )
        assertEquals(activationCompletion, store.download("green_pet"))
    }

    @Test
    fun `stale validation cannot remove a replacement installation`() {
        val keyValueStore = FakeCharacterPackKeyValueStore()
        val validatingProcess = CharacterPackLocalStore(keyValueStore, gson)
        val workerProcess = CharacterPackLocalStore(keyValueStore, gson)
        val oldInstallation = installed("green_pet")
        val replacement = installed("green_pet").copy(
            installedAtMs = 2L
        )
        assertTrue(validatingProcess.putInstalled(oldInstallation))
        assertTrue(workerProcess.putInstalled(replacement))

        val result = validatingProcess.removeInstalledIfCurrent(oldInstallation)

        assertEquals(CharacterPackConditionalWriteResult.STALE, result)
        assertEquals(replacement, validatingProcess.installed("green_pet"))
    }

    @Test
    fun `conditional removal also compares the installed version`() {
        val store = CharacterPackLocalStore(FakeCharacterPackKeyValueStore(), gson)
        val validatedInstallation = installed("green_pet")
        val replacement = validatedInstallation.copy(packVersion = 2, installedAtMs = 2L)
        assertTrue(store.putInstalled(replacement))

        val result = store.removeInstalledIfCurrent(validatedInstallation)

        assertEquals(CharacterPackConditionalWriteResult.STALE, result)
        assertEquals(replacement, store.installed("green_pet"))
    }

    @Test
    fun `conditional removal deletes only the installation that was validated`() {
        val store = CharacterPackLocalStore(FakeCharacterPackKeyValueStore(), gson)
        val installed = installed("green_pet")
        assertTrue(store.putInstalled(installed))

        val result = store.removeInstalledIfCurrent(installed)

        assertEquals(CharacterPackConditionalWriteResult.UPDATED, result)
        assertNull(store.installed("green_pet"))
    }

    @Test
    fun `invalid active state is recovered as a failed task`() {
        val store = CharacterPackLocalStore(FakeCharacterPackKeyValueStore(), gson)

        assertTrue(
            store.putDownload(
                CharacterPackDownloadState(
                    packId = "green_pet",
                    packVersion = 0,
                    downloadRequestId = "not-a-uuid",
                    status = CharacterPackDownloadStatus.DOWNLOADING,
                    totalBytes = Long.MAX_VALUE,
                    progress = 250,
                    activationRequestId = "also-invalid"
                )
            )
        )

        val recovered = store.download("green_pet")
        assertEquals(CharacterPackDownloadStatus.FAILED, recovered?.status)
        assertNull(recovered?.downloadRequestId)
        assertNull(recovered?.activationRequestId)
        assertEquals(CharacterPackLocalStore.MAX_PACKAGE_BYTES, recovered?.totalBytes)
        assertEquals(0, recovered?.progress)
    }

    private fun persistedStateJson(
        catalog: List<CharacterPackCatalogItem> = emptyList(),
        installed: Map<String, InstalledCharacterPack> = emptyMap(),
        downloads: Map<String, CharacterPackDownloadState> = emptyMap()
    ): String = gson.toJson(
        mapOf(
            "catalog" to catalog,
            "installed" to installed,
            "downloads" to downloads
        )
    )

    private fun installed(packId: String) = InstalledCharacterPack(
        packId = packId,
        packVersion = 1,
        displayName = packId,
        installedDirectory = "/data/character_packs/$packId/1-${UUID.randomUUID()}",
        installedAtMs = 1L
    )

    private fun queuedDownload(
        packId: String,
        requestId: String = UUID.randomUUID().toString()
    ) = CharacterPackDownloadState(
        packId = packId,
        packVersion = 1,
        downloadRequestId = requestId,
        status = CharacterPackDownloadStatus.QUEUED,
        totalBytes = 1_000L
    )

    private fun catalogItem(
        packId: String = "green_pet",
        isDefault: Boolean = true
    ) = CharacterPackCatalogItem(
        packId = packId,
        packVersion = 1,
        displayName = packId,
        isDefault = isDefault,
        previewUrl = "https://cdn.example.com/$packId.png",
        packageUrl = "https://cdn.example.com/$packId.zip",
        packageSha256 = "a".repeat(64),
        packageSizeBytes = 1_000L,
        manifestSchemaVersion = 1,
        updatedAtMs = 1L
    )

    private companion object {
        const val KEY_STATE = "character_pack_state_v2"
        const val KEY_INSTALLED_LEGACY = "character_pack_installed_v1"
    }
}

private class FakeCharacterPackKeyValueStore(
    val values: MutableMap<String, String> = mutableMapOf()
) : CharacterPackKeyValueStore {
    private val lock = ReentrantLock(true)
    private var lockCount = 0

    var failWrites: Boolean = false
    var beforeLock: ((Int) -> Unit)? = null
    var onRefreshFromOuterProcess: (() -> Unit)? = null

    override fun decodeString(key: String): String? = values[key]

    override fun encodeString(key: String, value: String): Boolean {
        if (failWrites) return false
        values[key] = value
        return true
    }

    override fun refreshFromOuterProcess() {
        onRefreshFromOuterProcess?.invoke()
    }

    override fun <T> withProcessLock(block: () -> T): T {
        val nextLock = synchronized(this) { ++lockCount }
        beforeLock?.invoke(nextLock)
        return lock.withLock(block)
    }
}
