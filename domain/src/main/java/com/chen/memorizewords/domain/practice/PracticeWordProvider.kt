package com.chen.memorizewords.domain.practice

import com.chen.memorizewords.domain.model.words.word.Word

enum class PracticeAvailability {
    AVAILABLE,
    NO_BOOK,
    NO_WORDS
}

interface PracticeWordProvider {
    suspend fun loadWords(
        selectedIds: LongArray?,
        randomCount: Int,
        defaultLimit: Int
    ): List<Word>

    suspend fun getPracticeAvailability(): PracticeAvailability

    suspend fun resolveBookId(): Long?

    suspend fun loadReviewWordsForPicker(): List<Word>
}
