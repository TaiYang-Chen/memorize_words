package com.chen.memorizewords.data.floating.local

import com.google.gson.JsonParser

/** Keys and detection retained only for the one-way V1-to-V2 local preference migration. */
internal object FloatingLegacyStorageKeys {
    const val SETTINGS_PAYLOAD = "floating_word_settings_payload_v2"
    const val DEVICE_PREFERENCES_BACKUP = "floating_device_preferences_legacy_payload_v2"

    fun containsDevicePreferences(payload: String): Boolean = runCatching {
        val objectPayload = JsonParser.parseString(payload).asJsonObject
        DEVICE_PREFERENCE_FIELDS.any(objectPayload::has)
    }.getOrDefault(false)

    private val DEVICE_PREFERENCE_FIELDS = setOf(
        "autoStartOnAppLaunch",
        "floatingBallX",
        "floatingBallY",
        "dockConfig",
        "dockState"
    )
}
