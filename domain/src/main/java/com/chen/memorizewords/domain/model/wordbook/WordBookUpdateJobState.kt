package com.chen.memorizewords.domain.model.wordbook

sealed interface WordBookUpdateJobState {
    data object Idle : WordBookUpdateJobState

    data class Running(
        val bookId: Long,
        val targetVersion: Long,
        val progress: Int
    ) : WordBookUpdateJobState

    data class Succeeded(
        val bookId: Long,
        val targetVersion: Long
    ) : WordBookUpdateJobState

    data class Failed(
        val bookId: Long,
        val targetVersion: Long,
        val message: String
    ) : WordBookUpdateJobState
}
