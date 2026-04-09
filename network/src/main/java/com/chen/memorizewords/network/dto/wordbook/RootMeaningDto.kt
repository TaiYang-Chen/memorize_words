package com.chen.memorizewords.network.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 璇嶆牴鍚箟鐨?DTO銆?
 */
@JsonClass(generateAdapter = false)
data class RootMeaningDto(
    val id: Long,
    val rootId: Long,
    val meaning: String,
    val examples: List<RootExampleDto> = emptyList()
)
