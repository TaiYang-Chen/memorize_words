package com.chen.memorizewords.data.repository.practice

import com.chen.memorizewords.domain.repository.practice.ListeningPracticePreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_LAST_LISTENING_MODE = "practice_listening_last_mode"
private const val KEY_MODE_SWITCH_HINT_SHOWN = "practice_listening_mode_switch_hint_shown"
private const val DEFAULT_LISTENING_MODE_NAME = "MEANING"

private val VALID_LISTENING_MODE_NAMES = setOf(
    "SPELLING",
    DEFAULT_LISTENING_MODE_NAME
)

internal fun normalizeListeningPracticeModeName(modeName: String?): String? {
    val normalized = modeName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return normalized.takeIf { VALID_LISTENING_MODE_NAMES.contains(it) }
}

interface ListeningPracticePreferencesStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

class MmkvListeningPracticePreferencesStore @Inject constructor(
    private val mmkv: com.tencent.mmkv.MMKV
) : ListeningPracticePreferencesStore {
    override fun getString(key: String): String? = mmkv.decodeString(key, null)

    override fun putString(key: String, value: String) {
        mmkv.encode(key, value)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return mmkv.decodeBool(key, defaultValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        mmkv.encode(key, value)
    }

    override fun remove(key: String) {
        mmkv.removeValueForKey(key)
    }
}

@Singleton
class ListeningPracticePreferencesRepositoryImpl @Inject constructor(
    private val store: ListeningPracticePreferencesStore
) : ListeningPracticePreferencesRepository {

    override suspend fun getLastListeningPracticeModeName(): String? {
        return normalizeListeningPracticeModeName(store.getString(KEY_LAST_LISTENING_MODE))
    }

    override suspend fun saveLastListeningPracticeModeName(modeName: String) {
        val normalized = normalizeListeningPracticeModeName(modeName) ?: return
        store.putString(KEY_LAST_LISTENING_MODE, normalized)
    }

    override suspend fun hasShownModeSwitchHint(): Boolean {
        return store.getBoolean(KEY_MODE_SWITCH_HINT_SHOWN, false)
    }

    override suspend fun markModeSwitchHintShown() {
        store.putBoolean(KEY_MODE_SWITCH_HINT_SHOWN, true)
    }

    fun clearLocalState() {
        store.remove(KEY_LAST_LISTENING_MODE)
        store.remove(KEY_MODE_SWITCH_HINT_SHOWN)
    }

    fun getDefaultListeningPracticeModeName(): String = DEFAULT_LISTENING_MODE_NAME
}
