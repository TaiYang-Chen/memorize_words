package com.chen.memorizewords.network.dto.wordbook

import com.squareup.moshi.JsonClass

/**
 * 璇嶆牴鐨勬暟鎹紶杈撳璞?(DTO)銆?
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
