package com.chen.memorizewords.data.word.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 鐠囧秵鐗撮崣妯圭秼锟?DTO锟?
 */
@JsonClass(generateAdapter = false)
data class RootVariantDto(
    val id: Long,
    val rootId: Long,
    val variant: String
)
