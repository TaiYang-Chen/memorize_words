package com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 鐠囧秵鐗撮崥顐＄疅锟?DTO锟?
 */
@JsonClass(generateAdapter = false)
data class RootMeaningDto(
    val id: Long,
    val rootId: Long,
    val meaning: String,
    val examples: List<RootExampleDto> = emptyList()
)
