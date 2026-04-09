package com.chen.memorizewords.network.dto.wordbook

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WordBookDto(
    val id: Long,
    val title: String,
    val category: String,
    val imgUrl: String,
    val description: String,
    val totalWords: Int,
    val learnedWords: Int,
    val contentVersion: Long = 0L,
    val updatedAt: Long = 0L,
    val isNew: Boolean = false,
    val isHot: Boolean = false,
    val isSelected: Boolean = false,
    val isPublic: Boolean,
    val createdByUserId: String?
)
