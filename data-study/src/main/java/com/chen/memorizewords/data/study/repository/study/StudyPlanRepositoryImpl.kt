package com.chen.memorizewords.data.study.repository.study

import com.chen.memorizewords.data.study.local.mmkv.plan.StudyPlanDataSource
import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StudyPlanRepositoryImpl @Inject constructor(
    private val studyPlanDataSource: StudyPlanDataSource,
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val directSyncLauncher: DirectSyncLauncher
) : StudyPlanRepository, StudyPlanLocalStatePort {

    override suspend fun saveStudyPlan(studyPlan: StudyPlan) {
        withContext(Dispatchers.IO) {
            studyPlanDataSource.saveStudyPlan(studyPlan)
        }
        upload(studyPlan)
    }

    override suspend fun getStudyPlan(): StudyPlan? {
        return studyPlanDataSource.getStudyPlan()
    }

    override fun getStudyPlanFlow(): Flow<StudyPlan?> {
        return studyPlanDataSource.getStudyPlanFlow()
    }

    override suspend fun saveStudyCount(dailyNewCount: Int, dailyReviewCount: Int) {
        val plan = withContext(Dispatchers.IO) {
            studyPlanDataSource.saveStudyCount(dailyNewCount, dailyReviewCount)
            studyPlanDataSource.getStudyPlan()
                ?: StudyPlan(dailyNewCount = dailyNewCount, dailyReviewCount = dailyReviewCount)
        }
        upload(plan)
    }

    override fun overwriteFromRemote(studyPlan: StudyPlan?) {
        if (studyPlan == null) {
            clearLocalState()
            return
        }
        runBlocking(Dispatchers.IO) {
            studyPlanDataSource.saveStudyPlan(studyPlan)
        }
    }

    override fun clearLocalState() {
        runBlocking(Dispatchers.IO) {
            studyPlanDataSource.clearStudyPlan()
        }
    }

    private fun upload(plan: StudyPlan) {
        directSyncLauncher.launch(
            operation = "study_plan",
            orderingKey = "study_plan",
            request = { remoteUserSyncDataSource.updateStudyPlan(plan) }
        )
    }
}
