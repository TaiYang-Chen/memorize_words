package com.chen.memorizewords.data.sync.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 词根变体 DTO。
 */
@JsonClass(generateAdapter = false)
data class RootVariantDto(
    val id: Long,
    val rootId: Long,
    val variant: String
)
