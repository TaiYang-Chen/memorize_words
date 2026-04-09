package com.chen.memorizewords.network.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 璇嶆牴鍙樹綋鐨?DTO銆?
 */
@JsonClass(generateAdapter = false)
data class RootVariantDto(
    val id: Long,
    val rootId: Long,
    val variant: String
)
