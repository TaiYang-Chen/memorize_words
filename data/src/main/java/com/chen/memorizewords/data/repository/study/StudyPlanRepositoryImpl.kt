package com.chen.memorizewords.data.repository.study

import com.chen.memorizewords.data.local.mmkv.plan.StudyPlanDataSource
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.repository.sync.StudyPlanSyncPayload
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.study.StudyPlan
import com.chen.memorizewords.domain.repository.StudyPlanRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StudyPlanRepositoryImpl @Inject constructor(
    private val studyPlanDataSource: StudyPlanDataSource,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : StudyPlanRepository {

    override suspend fun saveStudyPlan(studyPlan: StudyPlan) {
        withContext(Dispatchers.IO) {
            studyPlanDataSource.saveStudyPlan(studyPlan)
            syncOutboxDao.upsert(
                syncOutboxEntity(
                    bizType = SyncOutboxBizType.STUDY_PLAN,
                    bizKey = "study_plan",
                    operation = SyncOutboxOperation.UPSERT,
                    payload = gson.toJson(
                        StudyPlanSyncPayload(
                            dailyNewWords = studyPlan.dailyNewCount,
                            dailyReviewWords = studyPlan.dailyReviewCount,
                            testMode = studyPlan.testMode.name,
                            wordOrderType = studyPlan.wordOrderType.name
                        )
                    )
                )
            )
            syncOutboxWorkScheduler.scheduleDrain()
        }
    }

    override suspend fun getStudyPlan(): StudyPlan {
        return studyPlanDataSource.getStudyPlan()
    }

    override fun getStudyPlanFlow(): Flow<StudyPlan> {
        return studyPlanDataSource.getStudyPlanFlow()
    }

    override suspend fun saveStudyCount(dailyNewCount: Int, dailyReviewCount: Int) {
        withContext(Dispatchers.IO) {
            studyPlanDataSource.saveStudyCount(dailyNewCount, dailyReviewCount)
            val plan = studyPlanDataSource.getStudyPlan()
            syncOutboxDao.upsert(
                syncOutboxEntity(
                    bizType = SyncOutboxBizType.STUDY_PLAN,
                    bizKey = "study_plan",
                    operation = SyncOutboxOperation.UPSERT,
                    payload = gson.toJson(
                        StudyPlanSyncPayload(
                            dailyNewWords = dailyNewCount,
                            dailyReviewWords = dailyReviewCount,
                            testMode = plan.testMode.name,
                            wordOrderType = plan.wordOrderType.name
                        )
                    )
                )
            )
            syncOutboxWorkScheduler.scheduleDrain()
        }
    }
}
