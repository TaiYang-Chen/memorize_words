package com.chen.memorizewords.data.study.local.room.model.study.progress.word

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 杩欎釜鍗曡瘝鐜板湪澶勪簬浠€涔堝涔犵姸锟?
 */
@Entity(
    tableName = "word_learning_state",
    primaryKeys = ["word_id", "book_id"],
    indices = [
        Index("word_id"),
        Index("book_id"),
        Index("next_review_time"),
        Index(value = ["book_id", "user_status"]),
        Index(value = ["book_id", "next_review_time"])
    ],
)
data class WordLearningStateEntity(
    /** 鍗曡瘝ID */
    @ColumnInfo(name = "word_id")
    val wordId: Long,

    /** 涔︽湰ID */
    @ColumnInfo(name = "book_id")
    val bookId: Long,

    /** 鎬诲涔犳鏁帮紙鏂板 + 澶嶄範锟?*/
    @ColumnInfo(name = "total_learn_count")
    val totalLearnCount: Int,

    /** 鏈€杩戜竴娆″涔犳椂锟?*/
    @ColumnInfo(name = "last_learn_time")
    val lastLearnTime: Long,

    /** 涓嬫澶嶄範鏃堕棿锛堣蹇嗘洸绾匡級 */
    @ColumnInfo(name = "next_review_time")
    val nextReviewTime: Long,

    /** 鎺屾彙锟?0~5 */
    @ColumnInfo(name = "mastery_level")
    val masteryLevel: Int,

    /** 鐢ㄦ埛瀛︿範鐘舵€侊細0=姝ｅ父瀛︿範涓紝1=鐢ㄦ埛鏍囪涓哄凡鎺屾彙锟?=鐢ㄦ埛鏆傚仠瀛︿範锛堝澶畝鍗曪級 */
    @ColumnInfo(name = "user_status")
    val userStatus: Int = 0,

    /** SM-2 涓撶敤锛氳窛绂讳笂娆″涔犲ぉ锟?*/
    @ColumnInfo(name = "interval")
    val interval: Long = 0,

    /** SM-2 涓撶敤锛氳繛缁涔犳锟?*/
    @ColumnInfo(name = "repetition")
    val repetition: Int,

    /** SM-2 涓撶敤锛氭槗璁板洜锟?*/
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
