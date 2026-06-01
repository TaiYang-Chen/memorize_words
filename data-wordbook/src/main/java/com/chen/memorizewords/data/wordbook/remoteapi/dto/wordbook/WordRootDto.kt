package com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 鐠囧秵鐗撮惃鍕殶閹诡喕绱舵潏鎾愁嚠锟?(DTO)锟?
 */
@JsonClass(generateAdapter = false)
data class WordRootDto(
    val id: Long,
    val rootWord: String,
    val coreMeaning: String,
    val etymology: String?,
    val sourceLanguage: String,
    val difficulty: Int = 1,
    val tags: String? = null
)
