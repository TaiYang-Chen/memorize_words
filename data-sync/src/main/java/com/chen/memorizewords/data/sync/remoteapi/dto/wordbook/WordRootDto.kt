package com.chen.memorizewords.data.sync.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WordRootDto(
    val id: Long,
    val root: String = "",
    val meaning: String = "",
    val sourceLanguage: String = "",
    val difficulty: Int = 1,
    val tags: List<String> = emptyList(),
    val position: Int? = null,
    val etymology: String? = null
) {
    val rootWord: String get() = root
    val coreMeaning: String get() = meaning
}
