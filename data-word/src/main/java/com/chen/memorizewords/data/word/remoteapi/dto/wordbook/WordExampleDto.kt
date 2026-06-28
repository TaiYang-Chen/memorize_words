package com.chen.memorizewords.data.word.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WordExampleDto(
    val id: Long,
    val definitionId: Long? = null,
    val sentence: String = "",
    val translation: String? = null,
    val difficulty: Int = 3,
    val position: Int? = null,
    val wordId: Long = 0
) {
    val englishSentence: String get() = sentence
    val chineseTranslation: String? get() = translation
    val difficultyLevel: Int get() = difficulty
}
