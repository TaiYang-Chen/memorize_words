package com.chen.memorizewords.data.word.local.room.model.words.meta

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import com.chen.memorizewords.data.word.local.room.model.words.word.WordEntity

@Entity(
    tableName = "word_user_meta",
    primaryKeys = ["word_id"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
)
data class WordUserMetaEntity(
    @ColumnInfo(name = "word_id")
    val wordId: Long,

    @ColumnInfo(name = "memory_tip")
    val memoryTip: String? = null,

    @ColumnInfo(name = "mnemonic_image_url")
    val mnemonicImageUrl: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "root_memory_tip")
    val rootMemoryTip: String? = null,

    @ColumnInfo(name = "is_user_selected")
    val isUserSelected: Boolean = false
)
