package com.chen.memorizewords.domain.word.model

import com.chen.memorizewords.domain.word.model.enums.PartOfSpeech
import com.chen.memorizewords.domain.word.model.enums.WordLearningStatus

data class WordListRow(
    val wordId: Long,
    val word: String,
    val phonetic: String?,
    val partOfSpeech: PartOfSpeech = PartOfSpeech.UNKNOWN,
    val meanings: String,
    val masteryLevel: Int = 0,
    val isFavorite: Boolean = false,
    val learningStatus: WordLearningStatus = WordLearningStatus.TO_LEARN,
    val totalLearnCount: Int = 0,
    val lastLearnedAtMs: Long = 0L,
    val nextReviewAtMs: Long = 0L
) {
    val groupChar: Char
        get() = word.firstOrNull()?.uppercaseChar() ?: '#'
}
