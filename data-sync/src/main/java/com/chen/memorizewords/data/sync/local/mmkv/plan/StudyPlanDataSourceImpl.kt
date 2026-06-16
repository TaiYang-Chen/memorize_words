package com.chen.memorizewords.data.sync.local.mmkv.plan

import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
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

    private val _plan = MutableStateFlow<StudyPlan?>(readPlanFromStorage())
    private val plan: Flow<StudyPlan?> = _plan

    override suspend fun saveStudyCount(dailyNewCount: Int, dailyReviewCount: Int) {
        val current = _plan.value ?: readPlanFromStorage() ?: StudyPlan()
        val nextPlan = current.copy(
            dailyNewCount = dailyNewCount,
            dailyReviewCount = dailyReviewCount
        )
        saveStudyPlan(nextPlan)
    }

    override suspend fun saveStudyPlan(studyPlan: StudyPlan) {
        withContext(Dispatchers.IO) {
            val normalizedPlan = studyPlan.meaningChoiceOnly()
            mmkv.encode(dailyNewCountKey, normalizedPlan.dailyNewCount)
            mmkv.encode(dailyReviewCountKey, normalizedPlan.dailyReviewCount)
            mmkv.encode(testModeKey, normalizedPlan.testMode.name)
            mmkv.encode(wordOrderTypeKey, normalizedPlan.wordOrderType.name)
            _plan.value = normalizedPlan
        }
    }

    override suspend fun clearStudyPlan() {
        withContext(Dispatchers.IO) {
            mmkv.removeValueForKey(dailyNewCountKey)
            mmkv.removeValueForKey(dailyReviewCountKey)
            mmkv.removeValueForKey(testModeKey)
            mmkv.removeValueForKey(wordOrderTypeKey)
            _plan.value = null
        }
    }

    override suspend fun getStudyPlan(): StudyPlan? {
        return withContext(Dispatchers.IO) { readPlanFromStorage() }
    }

    override fun getStudyPlanFlow(): Flow<StudyPlan?> = plan

    private fun readPlanFromStorage(): StudyPlan? {
        if (!hasStoredPlan()) {
            return null
        }
        val orderTypeName = mmkv.decodeString(wordOrderTypeKey, WordOrderType.RANDOM.name)
        val orderType = runCatching { WordOrderType.valueOf(orderTypeName.orEmpty()) }
            .getOrDefault(WordOrderType.RANDOM)
        return StudyPlan(
            dailyNewCount = mmkv.decodeInt(dailyNewCountKey, StudyPlan().dailyNewCount),
            dailyReviewCount = mmkv.decodeInt(dailyReviewCountKey, StudyPlan().dailyReviewCount),
            testMode = LearningTestMode.MEANING_CHOICE,
            wordOrderType = orderType
        )
    }

    private fun hasStoredPlan(): Boolean {
        return mmkv.containsKey(dailyNewCountKey) ||
            mmkv.containsKey(dailyReviewCountKey) ||
            mmkv.containsKey(testModeKey) ||
            mmkv.containsKey(wordOrderTypeKey)
    }
}

private fun StudyPlan.meaningChoiceOnly(): StudyPlan {
    return if (testMode == LearningTestMode.MEANING_CHOICE) {
        this
    } else {
        copy(testMode = LearningTestMode.MEANING_CHOICE)
    }
}
