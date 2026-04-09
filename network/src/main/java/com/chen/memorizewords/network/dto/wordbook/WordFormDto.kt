package com.chen.memorizewords.network.dto.wordbook

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WordFormDto(
    val id: Long,
    val wordId: Long,
    val formWordId: Long? = null,
    val formType: String,
    val formText: String
)
