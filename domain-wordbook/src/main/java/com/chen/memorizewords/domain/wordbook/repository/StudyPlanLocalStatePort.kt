package com.chen.memorizewords.domain.wordbook.repository

import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan

interface StudyPlanLocalStatePort {
    fun overwriteFromRemote(studyPlan: StudyPlan?)
    fun clearLocalState()
}
