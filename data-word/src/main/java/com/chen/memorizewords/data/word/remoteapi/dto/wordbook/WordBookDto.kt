package com.chen.memorizewords.data.word.remoteapi.dto.wordbook

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
    val contentPackage: WordBookContentPackageDto? = null,
    val updatedAtMs: Long = 0L,
    val isNew: Boolean = false,
    val isHot: Boolean = false,
    val isSelected: Boolean = false,
    val isPublic: Boolean,
    val createdByUserId: String?
)

@JsonClass(generateAdapter = false)
data class WordBookContentPackageDto(
    val url: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0L,
    val contentType: String = "",
    val schemaVersion: Int = 0,
    val contentVersion: Long = 0L
)
