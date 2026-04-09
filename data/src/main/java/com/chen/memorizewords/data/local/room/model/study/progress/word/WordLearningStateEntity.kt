package com.chen.memorizewords.data.local.room.model.study.progress.word

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 这个单词现在处于什么学习状态
 */
@Entity(
    tableName = "word_learning_state",
    primaryKeys = ["word_id", "book_id"],
    indices = [
        Index("word_id"),
        Index("next_review_time")
    ],
)
data class WordLearningStateEntity(
    /** 单词ID */
    @ColumnInfo(name = "word_id")
    val wordId: Long,

    /** 书本ID */
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

    /** 掌握度 0~5 */
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
)
