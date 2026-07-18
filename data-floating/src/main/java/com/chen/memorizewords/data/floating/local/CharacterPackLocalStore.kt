package com.chen.memorizewords.data.floating.local

import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class CharacterPackLocalStore @Inject constructor(
    private val mmkv: MMKV,
    private val gson: Gson
) {
    private val catalogType = object : TypeToken<List<CharacterPackCatalogItem>>() {}.type
    private val installedType = object : TypeToken<Map<String, InstalledCharacterPack>>() {}.type
    private val downloadsType = object : TypeToken<Map<String, CharacterPackDownloadState>>() {}.type

    private val catalogState = MutableStateFlow(readCatalog())
    private val installedState = MutableStateFlow(readInstalled())
    private val downloadsState = MutableStateFlow(readDownloads())

    fun observeCatalog(): StateFlow<List<CharacterPackCatalogItem>> = catalogState.asStateFlow()
    fun observeInstalled(): StateFlow<Map<String, InstalledCharacterPack>> = installedState.asStateFlow()
    fun observeDownloads(): StateFlow<Map<String, CharacterPackDownloadState>> = downloadsState.asStateFlow()

    fun catalog(): List<CharacterPackCatalogItem> = catalogState.value
    fun installed(packId: String): InstalledCharacterPack? {
        val latest = readInstalled()
        if (latest != installedState.value) installedState.value = latest
        return latest[packId]
    }
    fun download(packId: String): CharacterPackDownloadState? = downloadsState.value[packId]

    @Synchronized
    fun replaceCatalog(items: List<CharacterPackCatalogItem>) {
        val normalized = items
            .filter { isSafePackId(it.packId) && it.packVersion > 0 }
            .distinctBy { it.packId }
            .sortedWith(compareBy<CharacterPackCatalogItem> { it.sortOrder }.thenBy { it.packId })
        mmkv.encode(KEY_CATALOG, gson.toJson(normalized))
        catalogState.value = normalized
    }

    @Synchronized
    fun putInstalled(item: InstalledCharacterPack) {
        val updated = installedState.value.toMutableMap().apply { put(item.packId, item) }.toMap()
        mmkv.encode(KEY_INSTALLED, gson.toJson(updated))
        installedState.value = updated
    }

    @Synchronized
    fun removeInstalled(packId: String) {
        val updated = installedState.value.toMutableMap().apply { remove(packId) }.toMap()
        mmkv.encode(KEY_INSTALLED, gson.toJson(updated))
        installedState.value = updated
    }

    @Synchronized
    fun putDownload(item: CharacterPackDownloadState) {
        val updated = downloadsState.value.toMutableMap().apply { put(item.packId, item) }.toMap()
        mmkv.encode(KEY_DOWNLOADS, gson.toJson(updated))
        downloadsState.value = updated
    }

    @Synchronized
    fun removeDownload(packId: String) {
        val updated = downloadsState.value.toMutableMap().apply { remove(packId) }.toMap()
        mmkv.encode(KEY_DOWNLOADS, gson.toJson(updated))
        downloadsState.value = updated
    }

    private fun readCatalog(): List<CharacterPackCatalogItem> = runCatching {
        gson.fromJson<List<CharacterPackCatalogItem>>(mmkv.decodeString(KEY_CATALOG), catalogType)
    }.getOrNull().orEmpty()

    private fun readInstalled(): Map<String, InstalledCharacterPack> = runCatching {
        gson.fromJson<Map<String, InstalledCharacterPack>>(mmkv.decodeString(KEY_INSTALLED), installedType)
    }.getOrNull().orEmpty()

    private fun readDownloads(): Map<String, CharacterPackDownloadState> = runCatching {
        gson.fromJson<Map<String, CharacterPackDownloadState>>(mmkv.decodeString(KEY_DOWNLOADS), downloadsType)
    }.getOrNull().orEmpty()

    companion object {
        private const val KEY_CATALOG = "character_pack_catalog_v1"
        private const val KEY_INSTALLED = "character_pack_installed_v1"
        private const val KEY_DOWNLOADS = "character_pack_downloads_v1"

        fun isSafePackId(value: String): Boolean =
            value.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}"))
    }
}
