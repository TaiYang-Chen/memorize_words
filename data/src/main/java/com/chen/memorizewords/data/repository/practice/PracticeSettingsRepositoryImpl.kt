package com.chen.memorizewords.data.repository.practice

import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.repository.sync.PracticeSettingsSyncPayload
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.practice.PracticeSettings
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
        private const val KEY_PLAY_WORD_SPELLING = "practice_play_word_spelling"
        private const val KEY_PLAY_CHINESE_MEANING = "practice_play_chinese_meaning"
        private const val KEY_SPEECH_PROVIDER = "practice_speech_provider"
        private const val LEGACY_SYNC_PROVIDER = "BACKEND_DEFAULT"
    }

    private val state = MutableStateFlow(readFromLocal())

    override fun observeSettings(): Flow<PracticeSettings> = state.asStateFlow()

    override suspend fun getSettings(): PracticeSettings = state.value

    override suspend fun saveSettings(settings: PracticeSettings) {
        mmkv.encode(KEY_SELECTED_BOOK_ID, settings.selectedBookId)
        mmkv.encode(KEY_INTERVAL_SECONDS, settings.intervalSeconds)
        mmkv.encode(KEY_LOOP_ENABLED, settings.loopEnabled)
        mmkv.encode(KEY_PLAY_WORD_SPELLING, settings.playWordSpelling)
        mmkv.encode(KEY_PLAY_CHINESE_MEANING, settings.playChineseMeaning)
        mmkv.removeValueForKey(KEY_SPEECH_PROVIDER)
        state.value = settings

        syncOutboxDao.upsert(
            syncOutboxEntity(
                bizType = SyncOutboxBizType.PRACTICE_SETTINGS,
                bizKey = "practice_settings",
                operation = SyncOutboxOperation.UPSERT,
                payload = gson.toJson(
                    PracticeSettingsSyncPayload(
                        selectedBookId = settings.selectedBookId,
                        intervalSeconds = settings.intervalSeconds,
                        loopEnabled = settings.loopEnabled,
                        playWordSpelling = settings.playWordSpelling,
                        playChineseMeaning = settings.playChineseMeaning,
                        provider = LEGACY_SYNC_PROVIDER
                    )
                )
            )
        )
        syncOutboxWorkScheduler.scheduleDrain()
    }

    fun overwriteFromRemote(settings: PracticeSettings) {
        mmkv.encode(KEY_SELECTED_BOOK_ID, settings.selectedBookId)
        mmkv.encode(KEY_INTERVAL_SECONDS, settings.intervalSeconds)
        mmkv.encode(KEY_LOOP_ENABLED, settings.loopEnabled)
        mmkv.encode(KEY_PLAY_WORD_SPELLING, settings.playWordSpelling)
        mmkv.encode(KEY_PLAY_CHINESE_MEANING, settings.playChineseMeaning)
        mmkv.removeValueForKey(KEY_SPEECH_PROVIDER)
        state.value = settings
    }

    fun clearLocalState() {
        mmkv.removeValueForKey(KEY_SELECTED_BOOK_ID)
        mmkv.removeValueForKey(KEY_INTERVAL_SECONDS)
        mmkv.removeValueForKey(KEY_LOOP_ENABLED)
        mmkv.removeValueForKey(KEY_PLAY_WORD_SPELLING)
        mmkv.removeValueForKey(KEY_PLAY_CHINESE_MEANING)
        mmkv.removeValueForKey(KEY_SPEECH_PROVIDER)
        state.value = readFromLocal()
    }

    private fun readFromLocal(): PracticeSettings {
        return PracticeSettings(
            selectedBookId = mmkv.decodeLong(KEY_SELECTED_BOOK_ID, 0L),
            intervalSeconds = mmkv.decodeInt(KEY_INTERVAL_SECONDS, 3).coerceAtLeast(1),
            loopEnabled = mmkv.decodeBool(KEY_LOOP_ENABLED, true),
            playWordSpelling = mmkv.decodeBool(KEY_PLAY_WORD_SPELLING, true),
            playChineseMeaning = mmkv.decodeBool(KEY_PLAY_CHINESE_MEANING, false)
        )
    }
}
