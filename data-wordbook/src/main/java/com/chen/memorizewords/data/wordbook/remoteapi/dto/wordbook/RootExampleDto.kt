package com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 鐠囧秵鐗撮崥顐＄疅缁€杞扮伐锟?DTO锟?
 */
@JsonClass(generateAdapter = false)
data class RootExampleDto(
    val id: Long,
    val meaningId: Long,
    val exampleSentence: String,
    val translation: String
)
