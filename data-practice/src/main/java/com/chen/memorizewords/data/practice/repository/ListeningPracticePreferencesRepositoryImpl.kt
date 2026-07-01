package com.chen.memorizewords.data.practice.repository

import com.chen.memorizewords.domain.practice.ListeningAnswerAreaPosition
import com.chen.memorizewords.domain.practice.ListeningPronunciationPreference
import com.chen.memorizewords.domain.practice.repository.ListeningPracticePreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_LAST_LISTENING_MODE = "practice_listening_last_mode"
private const val KEY_ANSWER_AREA_POSITION = "practice_listening_answer_area_position"
private const val KEY_PRONUNCIATION_PREFERENCE = "practice_listening_pronunciation_preference"
private const val KEY_MODE_SWITCH_HINT_SHOWN = "practice_listening_mode_switch_hint_shown"
private const val DEFAULT_LISTENING_MODE_NAME = "MEANING"
private val DEFAULT_ANSWER_AREA_POSITION = ListeningAnswerAreaPosition.MIDDLE
private val DEFAULT_PRONUNCIATION_PREFERENCE = ListeningPronunciationPreference.US

private val VALID_LISTENING_MODE_NAMES = setOf(
    "SPELLING",
    DEFAULT_LISTENING_MODE_NAME
)

internal fun normalizeListeningPracticeModeName(modeName: String?): String? {
    val normalized = modeName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return normalized.takeIf { VALID_LISTENING_MODE_NAMES.contains(it) }
}

internal fun normalizeListeningAnswerAreaPosition(
    positionName: String?
): ListeningAnswerAreaPosition {
    val normalized = positionName?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT_ANSWER_AREA_POSITION
    return runCatching { ListeningAnswerAreaPosition.valueOf(normalized) }
        .getOrDefault(DEFAULT_ANSWER_AREA_POSITION)
}

internal fun normalizeListeningPronunciationPreference(
    preferenceName: String?
): ListeningPronunciationPreference {
    val normalized = preferenceName?.trim()?.takeIf { it.isNotEmpty() }
        ?: return DEFAULT_PRONUNCIATION_PREFERENCE
    return runCatching { ListeningPronunciationPreference.valueOf(normalized) }
        .getOrDefault(DEFAULT_PRONUNCIATION_PREFERENCE)
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

    override suspend fun getAnswerAreaPosition(): ListeningAnswerAreaPosition {
        return normalizeListeningAnswerAreaPosition(store.getString(KEY_ANSWER_AREA_POSITION))
    }

    override suspend fun saveAnswerAreaPosition(position: ListeningAnswerAreaPosition) {
        store.putString(KEY_ANSWER_AREA_POSITION, position.name)
    }

    override suspend fun getPronunciationPreference(): ListeningPronunciationPreference {
        return normalizeListeningPronunciationPreference(store.getString(KEY_PRONUNCIATION_PREFERENCE))
    }

    override suspend fun savePronunciationPreference(preference: ListeningPronunciationPreference) {
        store.putString(KEY_PRONUNCIATION_PREFERENCE, preference.name)
    }

    override suspend fun hasShownModeSwitchHint(): Boolean {
        return store.getBoolean(KEY_MODE_SWITCH_HINT_SHOWN, false)
    }

    override suspend fun markModeSwitchHintShown() {
        store.putBoolean(KEY_MODE_SWITCH_HINT_SHOWN, true)
    }

    fun clearLocalState() {
        store.remove(KEY_LAST_LISTENING_MODE)
        store.remove(KEY_ANSWER_AREA_POSITION)
        store.remove(KEY_PRONUNCIATION_PREFERENCE)
        store.remove(KEY_MODE_SWITCH_HINT_SHOWN)
    }

    fun getDefaultListeningPracticeModeName(): String = DEFAULT_LISTENING_MODE_NAME
}
