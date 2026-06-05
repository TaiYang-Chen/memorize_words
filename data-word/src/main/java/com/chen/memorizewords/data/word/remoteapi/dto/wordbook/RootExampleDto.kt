package com.chen.memorizewords.data.word.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 词根含义示例 DTO。
 */
@JsonClass(generateAdapter = false)
data class RootExampleDto(
    val id: Long,
    val meaningId: Long,
    val exampleSentence: String,
    val translation: String
)
