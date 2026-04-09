package com.chen.memorizewords.domain.model.wordbook

data class WordBookUpdateUiState(
    val candidate: WordBookUpdateCandidate? = null,
    val settings: WordBookUpdateSettings = WordBookUpdateSettings(),
    val jobState: WordBookUpdateJobState = WordBookUpdateJobState.Idle,
    val deferredUntil: Long = 0L,
    val lastTrigger: WordBookUpdateTrigger? = null,
    val detailsVisible: Boolean = false,
    val settingsVisible: Boolean = false
)
