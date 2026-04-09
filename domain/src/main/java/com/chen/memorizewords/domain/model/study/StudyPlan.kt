package com.chen.memorizewords.domain.model.study

import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.domain.repository.WordOrderType

data class StudyPlan(
    val dailyNewCount: Int = 15,
    val dailyReviewCount: Int = 30,
    val testMode: LearningTestMode = LearningTestMode.MEANING_CHOICE,
    val wordOrderType: WordOrderType = WordOrderType.RANDOM
)
