package com.chen.memorizewords.data.practice.local.room.model.practice.exam

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chen.memorizewords.domain.practice.model.ExamCategory
import com.chen.memorizewords.domain.practice.model.ExamQuestionType

@Entity(
    tableName = "exam_practice_item",
    indices = [
        Index(value = ["word_id", "sort_order"]),
        Index(value = ["group_key"])
    ]
)
data class ExamPracticeItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "question_type")
    val questionType: ExamQuestionType,
    @ColumnInfo(name = "exam_category")
    val examCategory: ExamCategory,
    @ColumnInfo(name = "paper_name")
    val paperName: String,
    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: Int,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "group_key")
    val groupKey: String? = null,
    @ColumnInfo(name = "content_text")
    val contentText: String,
    @ColumnInfo(name = "context_text")
    val contextText: String? = null,
    @ColumnInfo(name = "options")
    val options: List<String> = emptyList(),
    @ColumnInfo(name = "answers")
    val answers: List<String> = emptyList(),
    @ColumnInfo(name = "left_items")
    val leftItems: List<String> = emptyList(),
    @ColumnInfo(name = "right_items")
    val rightItems: List<String> = emptyList(),
    @ColumnInfo(name = "answer_indexes")
    val answerIndexes: List<Int> = emptyList(),
    @ColumnInfo(name = "analysis_text")
    val analysisText: String? = null,
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long
) {
    init {
        require(difficultyLevel >= 0) { "difficultyLevel must be non-negative" }
        require(sortOrder >= 0) { "sortOrder must be non-negative" }
        require(cachedAt >= 0L) { "cachedAt must be non-negative" }
    }
}
