package com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WordFormDto(
    val id: Long,
    val type: String = "",
    val text: String = "",
    val formDefinition: String? = null,
    val targetWordId: Long? = null,
    val position: Int? = null,
    val wordId: Long = 0
) {
    val formWordId: Long? get() = targetWordId
    val formType: String get() = type
    val formText: String get() = text
}
