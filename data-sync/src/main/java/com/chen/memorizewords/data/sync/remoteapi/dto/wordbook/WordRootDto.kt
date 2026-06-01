package com.chen.memorizewords.data.sync.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 璇嶆牴鐨勬暟鎹紶杈撳�?(DTO)�?
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
