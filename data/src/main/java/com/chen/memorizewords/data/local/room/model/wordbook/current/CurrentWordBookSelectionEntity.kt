package com.chen.memorizewords.data.local.room.model.wordbook.current

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookEntity

@Entity(
    tableName = "current_word_book_selection",
    foreignKeys = [
        ForeignKey(
            entity = WordBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"], unique = true)
    ]
)
data class CurrentWordBookSelectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "selection_id")
    val selectionId: Int = SELECTION_ID,
    @ColumnInfo(name = "book_id")
    val bookId: Long
) {
    init {
        require(selectionId == SELECTION_ID) { "selectionId must always be $SELECTION_ID" }
    }

    companion object {
        const val SELECTION_ID: Int = 1
    }
}
