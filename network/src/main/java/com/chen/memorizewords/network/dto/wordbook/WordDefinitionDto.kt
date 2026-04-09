package com.chen.memorizewords.network.dto.wordbook

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WordDefinitionDto(
    val id: Long,
    val wordId: Long = 0,
    val partOfSpeech: String,
    val definition: String
)
