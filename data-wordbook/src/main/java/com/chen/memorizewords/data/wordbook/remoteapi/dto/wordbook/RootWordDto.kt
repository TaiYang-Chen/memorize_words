package com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class RootWordDto(
    val wordId: Long,
    val rootId: Long,
    val context: String,
    val partOfSpeech: String,
    val sequence: Int
)
