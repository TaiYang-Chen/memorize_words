package com.chen.memorizewords.domain.sync.model

import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan

data class LearningPrerequisitesSnapshot(
    val selectedBookId: Long,
    val studyPlan: StudyPlan
)
