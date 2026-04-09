package com.chen.memorizewords.network.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 璇嶆牴鍚箟绀轰緥鐨?DTO銆?
 */
@JsonClass(generateAdapter = false)
data class RootExampleDto(
    val id: Long,
    val meaningId: Long,
    val exampleSentence: String,
    val translation: String
)
