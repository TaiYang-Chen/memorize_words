package com.chen.memorizewords.data.sync.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WordDefinitionDto(
    val id: Long,
    val pos: String? = null,
    val meaning: String = "",
    val position: Int? = null,
    val wordId: Long = 0
) {
    val partOfSpeech: String get() = pos.orEmpty()
    val definition: String get() = meaning
}
