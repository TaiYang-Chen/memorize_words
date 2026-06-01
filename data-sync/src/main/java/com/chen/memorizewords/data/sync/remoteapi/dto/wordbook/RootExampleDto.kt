package com.chen.memorizewords.data.sync.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 璇嶆牴鍚箟绀轰緥�?DTO�?
 */
@JsonClass(generateAdapter = false)
data class RootExampleDto(
    val id: Long,
    val meaningId: Long,
    val exampleSentence: String,
    val translation: String
)
