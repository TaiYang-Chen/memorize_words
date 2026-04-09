package com.chen.memorizewords.domain.repository

import com.chen.memorizewords.domain.model.study.StudyPlan
import kotlinx.coroutines.flow.Flow

interface StudyPlanRepository {
    suspend fun saveStudyPlan(studyPlan: StudyPlan)
    suspend fun getStudyPlan(): StudyPlan
    fun getStudyPlanFlow(): Flow<StudyPlan>

    suspend fun saveStudyCount(dailyNewCount: Int, dailyReviewCount: Int)
}