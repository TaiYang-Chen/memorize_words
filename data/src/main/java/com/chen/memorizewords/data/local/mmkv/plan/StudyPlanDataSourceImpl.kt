package com.chen.memorizewords.data.local.mmkv.plan

import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.domain.model.study.StudyPlan
import com.chen.memorizewords.domain.repository.WordOrderType
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StudyPlanDataSourceImpl @Inject constructor(
    private val mmkv: MMKV
) : StudyPlanDataSource {

    private val dailyNewCountKey = "dailyNewWords"
    private val dailyReviewCountKey = "reviewMultiplier"
    private val testModeKey = "testMode"
    private val wordOrderTypeKey = "wordOrderType"

    private val _plan = MutableStateFlow(readPlanFromStorage())
    private val plan: Flow<StudyPlan> = _plan

    override suspend fun saveStudyCount(dailyNewCount: Int, dailyReviewCount: Int) {
        val current = _plan.value
        val nextPlan = current.copy(
            dailyNewCount = dailyNewCount,
            dailyReviewCount = dailyReviewCount
        )
        saveStudyPlan(nextPlan)
    }

    override suspend fun saveStudyPlan(studyPlan: StudyPlan) {
        withContext(Dispatchers.IO) {
            mmkv.encode(dailyNewCountKey, studyPlan.dailyNewCount)
            mmkv.encode(dailyReviewCountKey, studyPlan.dailyReviewCount)
            mmkv.encode(testModeKey, studyPlan.testMode.name)
            mmkv.encode(wordOrderTypeKey, studyPlan.wordOrderType.name)
            _plan.value = studyPlan
        }
    }

    override suspend fun clearStudyPlan() {
        withContext(Dispatchers.IO) {
            mmkv.removeValueForKey(dailyNewCountKey)
            mmkv.removeValueForKey(dailyReviewCountKey)
            mmkv.removeValueForKey(testModeKey)
            mmkv.removeValueForKey(wordOrderTypeKey)
            _plan.value = StudyPlan()
        }
    }

    override suspend fun getStudyPlan(): StudyPlan {
        return withContext(Dispatchers.IO) { readPlanFromStorage() }
    }

    override fun getStudyPlanFlow(): Flow<StudyPlan> = plan

    private fun readPlanFromStorage(): StudyPlan {
        val modeName = mmkv.decodeString(testModeKey, LearningTestMode.MEANING_CHOICE.name)
        val mode = runCatching { LearningTestMode.valueOf(modeName.orEmpty()) }
            .getOrDefault(LearningTestMode.MEANING_CHOICE)
        val orderTypeName = mmkv.decodeString(wordOrderTypeKey, WordOrderType.RANDOM.name)
        val orderType = runCatching { WordOrderType.valueOf(orderTypeName.orEmpty()) }
            .getOrDefault(WordOrderType.RANDOM)
        return StudyPlan(
            dailyNewCount = mmkv.decodeInt(dailyNewCountKey, StudyPlan().dailyNewCount),
            dailyReviewCount = mmkv.decodeInt(dailyReviewCountKey, StudyPlan().dailyReviewCount),
            testMode = mode,
            wordOrderType = orderType
        )
    }
}
