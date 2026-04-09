package com.chen.memorizewords.domain.model.wordbook

data class WordBookUpdateSettings(
    val foregroundAlertsEnabled: Boolean = true,
    val silentUpdateEnabled: Boolean = false,
    val silentUpdateNetworkPolicy: WordBookUpdateNetworkPolicy = WordBookUpdateNetworkPolicy.WIFI_ONLY,
    val remindLaterDurationMs: Long = DEFAULT_REMIND_LATER_DURATION_MS
) {
    companion object {
        const val DEFAULT_REMIND_LATER_DURATION_MS: Long = 24L * 60L * 60L * 1000L
    }
}
