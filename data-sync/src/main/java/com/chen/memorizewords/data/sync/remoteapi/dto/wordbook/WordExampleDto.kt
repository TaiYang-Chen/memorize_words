package com.chen.memorizewords.data.sync.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WordExampleDto(
    val id: Long,
    val wordId: Long,
    val definitionId: Long? = null,
    val englishSentence: String,
    val chineseTranslation: String? = null,
    val difficultyLevel: Int = 3
)
