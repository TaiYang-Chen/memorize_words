package com.chen.memorizewords.domain.model.study.progress.wordbook

/**
 * 单词书学习进度的领域模型
 */
data class WordBookProgress(

    val wordBookId: Long,

    val wordBookName: String,

    /** 已学单词数 */
    val learningCount: Int,

    /** 已掌握单词数 */
    val masteredCount: Int = 0,

    /** 单词总数 */
    val totalCount: Int = 0,

    /** 正确 */
    val correctCount: Int,
    /** 错误 */
    val wrongCount: Int,

    /** ???? */
    val studyDayCount: Int = 0,

    /** 最近一次学习日期 yyyy-MM-dd */
    val lastStudyDate: String = ""
)
