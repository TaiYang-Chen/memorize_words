package com.chen.memorizewords.data.local.mmkv.wordbookupdate

import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateNetworkPolicy
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateSettings
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class WordBookUpdateSettingsStore @Inject constructor(
    private val mmkv: MMKV
) {
    private val state = MutableStateFlow(load())

    fun get(): WordBookUpdateSettings = state.value

    fun observe(): StateFlow<WordBookUpdateSettings> = state.asStateFlow()

    fun save(settings: WordBookUpdateSettings) {
        mmkv.encode(KEY_FOREGROUND_ALERTS, settings.foregroundAlertsEnabled)
        mmkv.encode(KEY_SILENT_UPDATE, settings.silentUpdateEnabled)
        mmkv.encode(KEY_NETWORK_POLICY, settings.silentUpdateNetworkPolicy.name)
        state.value = settings
    }

    private fun load(): WordBookUpdateSettings {
        return WordBookUpdateSettings(
            foregroundAlertsEnabled = mmkv.decodeBool(KEY_FOREGROUND_ALERTS, true),
            silentUpdateEnabled = mmkv.decodeBool(KEY_SILENT_UPDATE, false),
            silentUpdateNetworkPolicy = runCatching {
                WordBookUpdateNetworkPolicy.valueOf(
                    mmkv.decodeString(KEY_NETWORK_POLICY, WordBookUpdateNetworkPolicy.WIFI_ONLY.name)
                        ?: WordBookUpdateNetworkPolicy.WIFI_ONLY.name
                )
            }.getOrDefault(WordBookUpdateNetworkPolicy.WIFI_ONLY)
        )
    }

    private companion object {
        private const val KEY_FOREGROUND_ALERTS = "device_wordbook_update_foreground_alerts"
        private const val KEY_SILENT_UPDATE = "device_wordbook_update_silent_enabled"
        private const val KEY_NETWORK_POLICY = "device_wordbook_update_network_policy"
    }
}
