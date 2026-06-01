package com.chen.memorizewords.startup

import android.content.Context
import com.tencent.mmkv.MMKV
import java.io.File

object LocalAssetResetter {
    private const val markerPrefs = "new_architecture_local_asset_reset"
    private const val keyResetVersion = "reset_version"
    private val policy = LocalAssetResetPolicy()

    fun resetLegacyAssetsIfNeeded(context: Context): LocalAssetResetReport {
        val markerPreferences = context.getSharedPreferences(markerPrefs, Context.MODE_PRIVATE)
        if (!policy.shouldReset(markerPreferences.getInt(keyResetVersion, 0))) {
            return LocalAssetResetReport(skipped = true)
        }

        MMKV.initialize(context)
        val mmkv = MMKV.defaultMMKV(MMKV.MULTI_PROCESS_MODE, null)
        val authSnapshot = AuthSnapshot.capture(mmkv, policy)

        val deletedDatabases = policy.resettableDatabaseNames.filter { name ->
            runCatching { context.deleteDatabase(name) }.getOrDefault(false)
        }

        mmkv.clearAll()
        authSnapshot.restore(mmkv)

        val deletedPaths = mutableListOf<String>()
        deleteSharedPreferencesExcept(context, "$markerPrefs.xml", deletedPaths)
        deleteChildren(context.cacheDir, deletedPaths)
        deleteChildren(context.externalCacheDir, deletedPaths)
        deleteChildren(context.codeCacheDir, deletedPaths)
        deleteChildrenExcept(context.filesDir, excludedNames = setOf("mmkv"), deletedPaths)

        markerPreferences.edit()
            .putInt(keyResetVersion, LocalAssetResetPolicy.RESET_VERSION)
            .putString("database_name", policy.resettableDatabaseNames.joinToString(","))
            .commit()

        return LocalAssetResetReport(
            skipped = false,
            deletedDatabases = deletedDatabases,
            deletedPaths = deletedPaths,
            preservedKeys = authSnapshot.keys
        )
    }

    private fun deleteSharedPreferencesExcept(
        context: Context,
        keepFileName: String,
        deletedPaths: MutableList<String>
    ) {
        val preferencesDir = File(context.applicationInfo.dataDir, "shared_prefs")
        preferencesDir.listFiles()
            ?.filter { file -> file.name != keepFileName }
            ?.forEach { file -> deletePath(file, deletedPaths) }
    }

    private fun deleteChildren(dir: File?, deletedPaths: MutableList<String>) {
        dir?.listFiles()?.forEach { file -> deletePath(file, deletedPaths) }
    }

    private fun deleteChildrenExcept(
        dir: File?,
        excludedNames: Set<String>,
        deletedPaths: MutableList<String>
    ) {
        dir?.listFiles()
            ?.filterNot { file -> file.name in excludedNames }
            ?.forEach { file -> deletePath(file, deletedPaths) }
    }

    private fun deletePath(file: File, deletedPaths: MutableList<String>) {
        val path = file.absolutePath
        val deleted = runCatching { file.deleteRecursively() }.getOrDefault(false)
        if (deleted) {
            deletedPaths += path
        }
    }

    private data class AuthSnapshot(
        val strings: Map<String, String>,
        val longs: Map<String, Long>,
        val booleans: Map<String, Boolean>
    ) {
        val keys: List<String>
            get() = strings.keys.toList() + longs.keys.toList() + booleans.keys.toList()

        fun restore(mmkv: MMKV) {
            strings.forEach { (key, value) -> mmkv.encode(key, value) }
            longs.forEach { (key, value) -> mmkv.encode(key, value) }
            booleans.forEach { (key, value) -> mmkv.encode(key, value) }
            mmkv.commit()
        }

        companion object {
            fun capture(mmkv: MMKV, policy: LocalAssetResetPolicy): AuthSnapshot {
                return AuthSnapshot(
                    strings = policy.preservedStringKeys.mapNotNull { key ->
                        mmkv.decodeString(key)?.let { value -> key to value }
                    }.toMap(),
                    longs = policy.preservedLongKeys
                        .filter { key -> mmkv.containsKey(key) }
                        .associateWith { key -> mmkv.decodeLong(key, 0L) },
                    booleans = policy.preservedBooleanKeys
                        .filter { key -> mmkv.containsKey(key) }
                        .associateWith { key -> mmkv.decodeBool(key, false) }
                )
            }
        }
    }
}

data class LocalAssetResetReport(
    val skipped: Boolean,
    val deletedDatabases: List<String> = emptyList(),
    val deletedPaths: List<String> = emptyList(),
    val preservedKeys: List<String> = emptyList()
) {
    fun summary(): String {
        return if (skipped) {
            "skipped"
        } else {
            "deletedDatabases=${deletedDatabases.size}, deletedPaths=${deletedPaths.size}, preservedKeys=${preservedKeys.size}"
        }
    }
}
