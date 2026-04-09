package com.chen.memorizewords.domain.model.study.progress.word

/**
 * 单词学习状态领域模型
 * 表示一个单词的学习进度和记忆状态
 */
data class WordLearningState(
    /** 单词ID */
    val wordId: Long,
    val bookId: Long,

    /** 总学习次数（新学 + 复习） */
    val totalLearnCount: Int = 0,
    
    /** 最近一次学习时间 */
    val lastLearnTime: Long = 0L,
    
    /** 下次复习时间（记忆曲线） */
    val nextReviewTime: Long = 0L,
    
    /** 掌握度 0~5 */
    val masteryLevel: Int = 0,

    /** 用户学习状态：0=正常学习中，1=用户标记为已掌握，2=用户暂停学习（如太简单） */
    val userStatus: Int = 0,
    
    /** SM-2 专用：距离上次学习天数 */
    val interval: Long = 0L,
    
    /** SM-2 专用：连续复习次数 */
    val repetition: Int = 0,
    
    /** SM-2 专用：易记因子 */
    val efactor: Double = 2.5
)