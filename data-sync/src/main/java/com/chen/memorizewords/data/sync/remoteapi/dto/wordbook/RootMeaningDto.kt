package com.chen.memorizewords.data.sync.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 词根含义 DTO。
 */
@JsonClass(generateAdapter = false)
data class RootMeaningDto(
    val id: Long,
    val rootId: Long,
    val meaning: String,
    val examples: List<RootExampleDto> = emptyList()
)
