package com.chen.memorizewords.domain.wordbook.model.study
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType

data class StudyPlan(
    val dailyNewCount: Int = 15,
    val dailyReviewCount: Int = 30,
    val testMode: LearningTestMode = LearningTestMode.MEANING_CHOICE,
    val wordOrderType: WordOrderType = WordOrderType.RANDOM
)
