package com.chen.memorizewords.domain.wordbook.model
data class WordBookUpdateUiState(
    val candidate: WordBookUpdateCandidate? = null,
    val settings: WordBookUpdateSettings = WordBookUpdateSettings(),
    val jobState: WordBookUpdateJobState = WordBookUpdateJobState.Idle,
    val deferredUntilMs: Long = 0L,
    val lastTrigger: WordBookUpdateTrigger? = null,
    val detailsVisible: Boolean = false,
    val settingsVisible: Boolean = false
)
