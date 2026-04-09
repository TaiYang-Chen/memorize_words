package com.chen.memorizewords.domain.model.study.favorites

/**
 * 生词表
 */
data class WordFavorites(
    val id: Long = 0,

    // 关联字段
    val wordId: Long,

    // 核心展示信息（冗余存储，避免频繁JOIN）
    val word: String,

    val definitions: String,

    val phonetic: String?,

    // 学习管理字段
    val addedDate: String
)