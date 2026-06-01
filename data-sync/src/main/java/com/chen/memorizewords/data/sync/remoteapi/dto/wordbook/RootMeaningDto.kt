package com.chen.memorizewords.data.sync.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 璇嶆牴鍚箟�?DTO�?
 */
@JsonClass(generateAdapter = false)
data class RootMeaningDto(
    val id: Long,
    val rootId: Long,
    val meaning: String,
    val examples: List<RootExampleDto> = emptyList()
)
