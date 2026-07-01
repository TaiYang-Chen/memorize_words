package com.chen.memorizewords.data.practice.repository

import com.chen.memorizewords.domain.sync.PracticeSettingsSyncPayload
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.practice.AudioLoopPlaybackMode
import com.chen.memorizewords.domain.practice.AudioLoopPlayOrder
import com.chen.memorizewords.domain.practice.PracticeSettings
import com.chen.memorizewords.domain.practice.PracticeSettingsLocalStatePort
import com.chen.memorizewords.domain.practice.PracticeSettingsRepository
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
    private val SyncOutboxWriter: SyncOutboxWriter,
    private val gson: Gson
) : PracticeSettingsRepository, PracticeSettingsLocalStatePort {

    companion object {
        private const val KEY_SELECTED_BOOK_ID = "practice_selected_book_id"
        private const val KEY_INTERVAL_SECONDS = "practice_interval_seconds"
        private const val KEY_LOOP_ENABLED = "practice_loop_enabled"
        private const val KEY_SHOW_PHONETIC = "practice_show_phonetic"
        private const val KEY_SHOW_MEANING = "practice_show_meaning"
        private const val KEY_AUDIO_LOOP_PLAYBACK_MODE = "practice_audio_loop_playback_mode"
        private const val KEY_AUDIO_LOOP_PLAY_TIMES = "practice_audio_loop_play_times"
        private const val KEY_AUDIO_LOOP_WORD_REPEAT_TIMES = "practice_audio_loop_word_repeat_times"
        private const val KEY_AUDIO_LOOP_EXAMPLE_REPEAT_TIMES = "practice_audio_loop_example_repeat_times"
        private const val KEY_AUDIO_LOOP_DICTATION_PAUSE_SECONDS = "practice_audio_loop_dictation_pause_seconds"
        private const val KEY_AUDIO_LOOP_REVEAL_DELAY_SECONDS = "practice_audio_loop_reveal_delay_seconds"
        private const val KEY_AUDIO_LOOP_PLAYBACK_SPEED = "practice_audio_loop_playback_speed"
        private const val KEY_AUDIO_LOOP_TIMED_STOP_MINUTES = "practice_audio_loop_timed_stop_minutes"
        private const val KEY_AUDIO_LOOP_KEEP_SCREEN_ON = "practice_audio_loop_keep_screen_on"
        private const val KEY_AUDIO_LOOP_PLAY_ORDER = "practice_audio_loop_play_order"
        private const val KEY_SPEECH_PROVIDER = "practice_speech_provider"
        private const val SYNC_PROVIDER = "BAIDU"
    }

    private val state = MutableStateFlow(readFromLocal())

    override fun observeSettings(): Flow<PracticeSettings> = state.asStateFlow()

    override suspend fun getSettings(): PracticeSettings = state.value

    override suspend fun saveSettings(settings: PracticeSettings) {
        val normalized = normalizeSettings(settings)
        writeSettingsToLocal(normalized)
        state.value = normalized

        SyncOutboxWriter.enqueueLatest(
            bizType = OutboxTopic.PRACTICE_SETTINGS,
            bizKey = "practice_settings",
            operation = SyncOperation.UPSERT,
            payload = gson.toJson(
                PracticeSettingsSyncPayload(
                    selectedBookId = normalized.selectedBookId,
                    intervalSeconds = normalized.intervalSeconds,
                    loopEnabled = normalized.loopEnabled,
                    showPhonetic = normalized.showPhonetic,
                    showMeaning = normalized.showMeaning,
                    playbackMode = normalized.playbackMode.name,
                    playTimes = normalized.playTimes,
                    wordRepeatTimes = normalized.wordRepeatTimes,
                    exampleRepeatTimes = normalized.exampleRepeatTimes,
                    dictationPauseSeconds = normalized.dictationPauseSeconds,
                    revealDelaySeconds = normalized.revealDelaySeconds,
                    playbackSpeed = normalized.playbackSpeed,
                    timedStopMinutes = normalized.timedStopMinutes,
                    keepScreenOn = normalized.keepScreenOn,
                    playOrder = normalized.playOrder.name,
                    provider = SYNC_PROVIDER
                )
            )
        )
    }

    override fun overwriteFromRemote(settings: PracticeSettings) {
        val normalized = normalizeSettings(settings)
        writeSettingsToLocal(normalized)
        state.value = normalized
    }

    override fun clearLocalState() {
        mmkv.removeValueForKey(KEY_SELECTED_BOOK_ID)
        mmkv.removeValueForKey(KEY_INTERVAL_SECONDS)
        mmkv.removeValueForKey(KEY_LOOP_ENABLED)
        mmkv.removeValueForKey(KEY_SHOW_PHONETIC)
        mmkv.removeValueForKey(KEY_SHOW_MEANING)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_PLAYBACK_MODE)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_PLAY_TIMES)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_WORD_REPEAT_TIMES)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_EXAMPLE_REPEAT_TIMES)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_DICTATION_PAUSE_SECONDS)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_REVEAL_DELAY_SECONDS)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_PLAYBACK_SPEED)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_TIMED_STOP_MINUTES)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_KEEP_SCREEN_ON)
        mmkv.removeValueForKey(KEY_AUDIO_LOOP_PLAY_ORDER)
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
                playTimes = mmkv.decodeInt(KEY_AUDIO_LOOP_PLAY_TIMES, 1),
                wordRepeatTimes = mmkv.decodeInt(KEY_AUDIO_LOOP_WORD_REPEAT_TIMES, 1),
                exampleRepeatTimes = mmkv.decodeInt(KEY_AUDIO_LOOP_EXAMPLE_REPEAT_TIMES, 1),
                dictationPauseSeconds = mmkv.decodeInt(KEY_AUDIO_LOOP_DICTATION_PAUSE_SECONDS, 5),
                revealDelaySeconds = mmkv.decodeInt(KEY_AUDIO_LOOP_REVEAL_DELAY_SECONDS, 0),
                playbackSpeed = mmkv.decodeFloat(KEY_AUDIO_LOOP_PLAYBACK_SPEED, 1.0f),
                timedStopMinutes = mmkv.decodeInt(KEY_AUDIO_LOOP_TIMED_STOP_MINUTES, 0),
                keepScreenOn = mmkv.decodeBool(KEY_AUDIO_LOOP_KEEP_SCREEN_ON, false),
                playOrder = runCatching {
                    AudioLoopPlayOrder.valueOf(
                        mmkv.decodeString(
                            KEY_AUDIO_LOOP_PLAY_ORDER,
                            AudioLoopPlayOrder.SEQUENTIAL.name
                        ).orEmpty()
                    )
                }.getOrDefault(AudioLoopPlayOrder.SEQUENTIAL)
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
        mmkv.encode(KEY_AUDIO_LOOP_WORD_REPEAT_TIMES, settings.wordRepeatTimes)
        mmkv.encode(KEY_AUDIO_LOOP_EXAMPLE_REPEAT_TIMES, settings.exampleRepeatTimes)
        mmkv.encode(KEY_AUDIO_LOOP_DICTATION_PAUSE_SECONDS, settings.dictationPauseSeconds)
        mmkv.encode(KEY_AUDIO_LOOP_REVEAL_DELAY_SECONDS, settings.revealDelaySeconds)
        mmkv.encode(KEY_AUDIO_LOOP_PLAYBACK_SPEED, settings.playbackSpeed)
        mmkv.encode(KEY_AUDIO_LOOP_TIMED_STOP_MINUTES, settings.timedStopMinutes)
        mmkv.encode(KEY_AUDIO_LOOP_KEEP_SCREEN_ON, settings.keepScreenOn)
        mmkv.encode(KEY_AUDIO_LOOP_PLAY_ORDER, settings.playOrder.name)
        mmkv.removeValueForKey(KEY_SPEECH_PROVIDER)
    }

    private fun normalizeSettings(settings: PracticeSettings): PracticeSettings {
        return settings.copy(
            intervalSeconds = settings.intervalSeconds.coerceAtLeast(0),
            playTimes = settings.playTimes.coerceAtLeast(1),
            wordRepeatTimes = settings.wordRepeatTimes.coerceAtLeast(1),
            exampleRepeatTimes = settings.exampleRepeatTimes.coerceAtLeast(1),
            dictationPauseSeconds = settings.dictationPauseSeconds.coerceAtLeast(0),
            revealDelaySeconds = settings.revealDelaySeconds.coerceAtLeast(0),
            playbackSpeed = settings.playbackSpeed.coerceIn(0.5f, 2.0f),
            timedStopMinutes = settings.timedStopMinutes.coerceAtLeast(0)
        )
    }
}
