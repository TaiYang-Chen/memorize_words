package com.chen.memorizewords.data.word.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 词根的数据传输对象（DTO）。
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
