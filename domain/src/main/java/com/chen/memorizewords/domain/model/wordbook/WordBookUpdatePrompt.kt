package com.chen.memorizewords.domain.model.wordbook

data class WordBookUpdatePrompt(
    val candidate: WordBookUpdateCandidate,
    val trigger: WordBookUpdateTrigger,
    val summaryText: String? = null,
    val deepLink: String? = null,
    val collapseKey: String? = null
)
