package com.chen.memorizewords.data.sync.local.mmkv.plan

import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import kotlinx.coroutines.flow.Flow


interface StudyPlanDataSource {
    suspend fun saveStudyCount(
        dailyNewCount: Int,
        dailyReviewCount: Int
    )

    suspend fun saveStudyPlan(studyPlan: StudyPlan)
    suspend fun clearStudyPlan()
    suspend fun getStudyPlan(): StudyPlan?
    fun getStudyPlanFlow(): Flow<StudyPlan?>
}
