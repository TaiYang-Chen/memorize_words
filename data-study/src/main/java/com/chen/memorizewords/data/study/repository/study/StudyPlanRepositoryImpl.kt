package com.chen.memorizewords.data.study.repository.study

import com.chen.memorizewords.data.study.local.mmkv.plan.StudyPlanDataSource
import com.chen.memorizewords.domain.sync.StudyPlanSyncPayload
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class StudyPlanRepositoryImpl @Inject constructor(
    private val studyPlanDataSource: StudyPlanDataSource,
    private val SyncOutboxWriter: SyncOutboxWriter,    private val gson: Gson
) : StudyPlanRepository, StudyPlanLocalStatePort {

    override suspend fun saveStudyPlan(studyPlan: StudyPlan) {
        val normalizedPlan = studyPlan.meaningChoiceOnly()
        withContext(Dispatchers.IO) {
            studyPlanDataSource.saveStudyPlan(normalizedPlan)
            SyncOutboxWriter.enqueueLatest(
                bizType = OutboxTopic.STUDY_PLAN,
                bizKey = "study_plan",
                operation = SyncOperation.UPSERT,
                payload = gson.toJson(
                    StudyPlanSyncPayload(
                        dailyNewWords = normalizedPlan.dailyNewCount,
                        dailyReviewWords = normalizedPlan.dailyReviewCount,
                        testMode = normalizedPlan.testMode.name,
                        wordOrderType = normalizedPlan.wordOrderType.name
                    )
                )
            )
        }
    }

    override suspend fun getStudyPlan(): StudyPlan? {
        return studyPlanDataSource.getStudyPlan()?.meaningChoiceOnly()
    }

    override fun getStudyPlanFlow(): Flow<StudyPlan?> {
        return studyPlanDataSource.getStudyPlanFlow().map { it?.meaningChoiceOnly() }
    }

    override suspend fun saveStudyCount(dailyNewCount: Int, dailyReviewCount: Int) {
        withContext(Dispatchers.IO) {
            studyPlanDataSource.saveStudyCount(dailyNewCount, dailyReviewCount)
            val plan = studyPlanDataSource.getStudyPlan()
                ?: StudyPlan(dailyNewCount = dailyNewCount, dailyReviewCount = dailyReviewCount)
            val normalizedPlan = plan.meaningChoiceOnly()
            SyncOutboxWriter.enqueueLatest(
                bizType = OutboxTopic.STUDY_PLAN,
                bizKey = "study_plan",
                operation = SyncOperation.UPSERT,
                payload = gson.toJson(
                    StudyPlanSyncPayload(
                        dailyNewWords = dailyNewCount,
                        dailyReviewWords = dailyReviewCount,
                        testMode = normalizedPlan.testMode.name,
                        wordOrderType = normalizedPlan.wordOrderType.name
                    )
                )
            )
        }
    }

    override fun overwriteFromRemote(studyPlan: StudyPlan?) {
        if (studyPlan == null) {
            clearLocalState()
            return
        }
        runBlocking(Dispatchers.IO) {
            studyPlanDataSource.saveStudyPlan(studyPlan.meaningChoiceOnly())
        }
    }

    override fun clearLocalState() {
        runBlocking(Dispatchers.IO) {
            studyPlanDataSource.clearStudyPlan()
        }
    }
}

private fun StudyPlan.meaningChoiceOnly(): StudyPlan {
    return if (testMode == LearningTestMode.MEANING_CHOICE) {
        this
    } else {
        copy(testMode = LearningTestMode.MEANING_CHOICE)
    }
}
