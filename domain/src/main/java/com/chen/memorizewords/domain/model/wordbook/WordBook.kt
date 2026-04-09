package com.chen.memorizewords.domain.model.wordbook

data class WordBook(
    val id: Long,
    val title: String,
    val category: String,
    val imgUrl: String,
    val description: String,
    val totalWords: Int,
    val contentVersion: Long = 0L,
    val isNew: Boolean = false,
    val isHot: Boolean = false,
    val isSelected: Boolean = false,
    val isPublic: Boolean,
    val createdByUserId: String?
)
