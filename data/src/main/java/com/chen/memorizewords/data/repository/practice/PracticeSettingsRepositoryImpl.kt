package com.chen.memorizewords.data.repository.practice

import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.repository.sync.PracticeSettingsSyncPayload
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.practice.PracticeSettings
import com.chen.memorizewords.domain.model.practice.AudioLoopPlaybackMode
import com.chen.memorizewords.domain.repository.practice.PracticeSettingsRepository
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class PracticeSettingsRepositoryImpl @Inject constructor(
    private val mmkv: MMKV,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : PracticeSettingsRepository {

    companion object {
        private const val KEY_SELECTED_BOOK_ID = "practice_selected_book_id"
        private const val KEY_INTERVAL_SECONDS = "practice_interval_seconds"
        private const val KEY_LOOP_ENABLED = "practice_loop_enabled"
        private const val KEY_SHOW_PHONETIC = "practice_show_phonetic"
        private const val KEY_SHOW_MEANING = "practice_show_meaning"
        private const val KEY_AUDIO_LOOP_PLAYBACK_MODE = "practice_audio_loop_playback_mode"
        private const val KEY_AUDIO_LOOP_PLAY_TIMES = "practice_audio_loop_play_times"
        private const val KEY_SPEECH_PROVIDER = "practice_speech_provider"
        private const val LEGACY_SYNC_PROVIDER = "BACKEND_DEFAULT"
    }

    private val state = MutableStateFlow(readFromLocal())

    override fun observeSettings(): Flow<PracticeSettings> = state.asStateFlow()

    override suspend fun getSettings(): PracticeSettings = state.value

    override suspend fun saveSettings(settings: PracticeSettings) {
        val normalized = normalizeSettings(settings)
        writeSettingsToLocal(normalized)
        state.value = normalized

        syncOutboxDao.upsert(
            syncOutboxEntity(
                bizType = SyncOutboxBizType.PRACTICE_SETTINGS,
                bizKey = "practice_settings",
                operation = SyncOutboxOperation.UPSERT,
                payload = gson.toJson(
                    PracticeSettingsSyncPayload(
                        selectedBookId = normalized.selectedBookId,
                        intervalSeconds = normalized.intervalSeconds,
                        loopEnabled = normalized.loopEnabled,
                        showPhonetic = normalized.showPhonetic,
                        showMeaning = normalized.showMeaning,
                        playbackMode = normalized.playbackMode.name,
                        playTimes = normalized.playTimes,
                        provider = LEGACY_SYNC_PROVIDER
                    )
                )
            )
        )
        syncOutboxWorkScheduler.scheduleDrain()
    }

    fun overwriteFromRemote(settings: PracticeSettings) {
        val normalized = normalizeSettings(settings)
        writeSettingsToLocal(normalized)
        state.value = normalized
    }

    fun clearLocalState() {
        mmkv.removeValueForKey(KEY_SELECTED_BOOK_ID)
        mmkv.removeValueForKey(KEY_INTERVAL_SECONDS)
        mmkv.removeValueForKey(KEY_LOOP_ENABLED)
        mmkv.removeValueForKey(KEY_SHOW_PHONETIC)
        mmkv.removeValueForKey(KEY_SHOW_MEANING)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_PLAYBACK_MODE)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_PLAY_TIMES)
        mmkv.removeValueForKey(KEY_SPEECH_PROVIDER)
        state.value = readFromLocal()
    }

    private fun readFromLocal(): PracticeSettings {
        return normalizeSettings(
            PracticeSettings(
                selectedBookId = mmkv.decodeLong(KEY_SELECTED_BOOK_ID, 0L),
                intervalSeconds = mmkv.decodeInt(KEY_INTERVAL_SECONDS, 3).coerceAtLeast(0),
                loopEnabled = mmkv.decodeBool(KEY_LOOP_ENABLED, true),
                showPhonetic = mmkv.decodeBool(KEY_SHOW_PHONETIC, true),
                showMeaning = mmkv.decodeBool(KEY_SHOW_MEANING, false),
                playbackMode = runCatching {
                    AudioLoopPlaybackMode.valueOf(
                        mmkv.decodeString(
                            KEY_AUDIO_LOOP_PLAYBACK_MODE,
                            AudioLoopPlaybackMode.WORD_ONLY.name
                        ).orEmpty()
                    )
                }.getOrDefault(AudioLoopPlaybackMode.WORD_ONLY),
                playTimes = mmkv.decodeInt(KEY_AUDIO_LOOP_PLAY_TIMES, 1).coerceAtLeast(1)
            )
        )
    }

    private fun writeSettingsToLocal(settings: PracticeSettings) {
        mmkv.encode(KEY_SELECTED_BOOK_ID, settings.selectedBookId)
        mmkv.encode(KEY_INTERVAL_SECONDS, settings.intervalSeconds)
        mmkv.encode(KEY_LOOP_ENABLED, settings.loopEnabled)
        mmkv.encode(KEY_SHOW_PHONETIC, settings.showPhonetic)
        mmkv.encode(KEY_SHOW_MEANING, settings.showMeaning)
        mmkv.encode(KEY_AUDIO_LOOP_PLAYBACK_MODE, settings.playbackMode.name)
        mmkv.encode(KEY_AUDIO_LOOP_PLAY_TIMES, settings.playTimes)
        mmkv.removeValueForKey(KEY_SPEECH_PROVIDER)
    }

    private fun normalizeSettings(settings: PracticeSettings): PracticeSettings {
        return settings.copy(
            intervalSeconds = settings.intervalSeconds.coerceAtLeast(0),
            playTimes = settings.playTimes.coerceAtLeast(1)
        )
    }
}
