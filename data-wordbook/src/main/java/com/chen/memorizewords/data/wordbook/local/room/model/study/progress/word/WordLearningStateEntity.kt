package com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordEntity

/**
 * 记录这个单词当前所处的学习状态。
 */
@Entity(
    tableName = "word_learning_state",
    primaryKeys = ["word_id", "book_id"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("word_id"),
        Index("book_id"),
        Index("next_review_time"),
        Index(value = ["book_id", "user_status"]),
        Index(value = ["book_id", "next_review_time"]),
        Index(value = ["book_id", "user_status", "next_review_time", "last_learn_time", "word_id"])
    ],
)
data class WordLearningStateEntity(
    /** 单词 ID */
    @ColumnInfo(name = "word_id")
    val wordId: Long,

    /** 书本 ID */
    @ColumnInfo(name = "book_id")
    val bookId: Long,

    /** 总学习次数（新学 + 复习） */
    @ColumnInfo(name = "total_learn_count")
    val totalLearnCount: Int,

    /** 最近一次学习时间 */
    @ColumnInfo(name = "last_learn_time")
    val lastLearnTime: Long,

    /** 下次复习时间（记忆曲线） */
    @ColumnInfo(name = "next_review_time")
    val nextReviewTime: Long,

    /** 掌握度：0~5 */
    @ColumnInfo(name = "mastery_level")
    val masteryLevel: Int,

    /** 用户学习状态：0=正常学习中，1=用户标记为已掌握，2=用户暂停学习（如太简单） */
    @ColumnInfo(name = "user_status")
    val userStatus: Int = 0,

    /** SM-2 专用：距离上次学习天数 */
    @ColumnInfo(name = "interval")
    val interval: Long = 0,

    /** SM-2 专用：连续复习次数 */
    @ColumnInfo(name = "repetition")
    val repetition: Int,

    /** SM-2 专用：易记因子 */
    @ColumnInfo(name = "efactor")
    val efactor: Double
) {
    init {
        require(totalLearnCount >= 0) { "totalLearnCount must be non-negative" }
        require(lastLearnTime >= 0L) { "lastLearnTime must be non-negative" }
        require(nextReviewTime >= 0L) { "nextReviewTime must be non-negative" }
        require(masteryLevel in 0..5) { "masteryLevel must be between 0 and 5" }
        require(userStatus in 0..2) { "userStatus must be between 0 and 2" }
        require(interval >= 0L) { "interval must be non-negative" }
        require(repetition >= 0) { "repetition must be non-negative" }
        require(efactor >= 0.0) { "efactor must be non-negative" }
    }
}
