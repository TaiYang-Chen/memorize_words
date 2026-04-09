package com.chen.memorizewords.data.local.room.model.wordbook.wordbook

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_book")
data class WordBookEntity(
    @PrimaryKey
    val id: Long,
    @ColumnInfo(name = "name")
    val title: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "image")
    val imgUrl: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "total_words")
    val totalWords: Int,
    @ColumnInfo(name = "content_version")
    val contentVersion: Long = 0L,
    @ColumnInfo(name = "is_new")
    val isNew: Boolean,
    @ColumnInfo(name = "is_hot")
    val isHot: Boolean,
    @ColumnInfo(name = "is_selected")
    val isSelected: Boolean,
    @ColumnInfo(name = "is_public")
    val isPublic: Boolean,
    @ColumnInfo(name = "created_by_user_id")
    val createdByUserId: String?
)

