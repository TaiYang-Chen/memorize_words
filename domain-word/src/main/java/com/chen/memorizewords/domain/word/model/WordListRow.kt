package com.chen.memorizewords.domain.word.model
import com.chen.memorizewords.domain.word.model.enums.PartOfSpeech

data class WordListRow(
    val wordId: Long,
    val word: String,
    val phonetic: String?,
    val partOfSpeech: PartOfSpeech = PartOfSpeech.UNKNOWN,
    val meanings: String,
    val masteryLevel: Int = 0
) {
    val groupChar: Char
        get() = word.firstOrNull()?.uppercaseChar() ?: '#'
}