package com.chen.memorizewords.domain.study.repository.record

import com.chen.memorizewords.domain.study.model.learning.DailyProgressEvaluation
import com.chen.memorizewords.domain.study.model.learning.DailyProgressProjectionTask
import com.chen.memorizewords.domain.study.model.learning.DailyProgressTransition

interface DailyStudyProjectionQueue {
    suspend fun getById(clientEventId: String): DailyProgressProjectionTask?
    suspend fun getPending(limit: Int = 100): List<DailyProgressProjectionTask>
    suspend fun delete(clientEventId: String)
}

interface DailyStudyProjectionStore {
    suspend fun apply(evaluation: DailyProgressEvaluation): DailyProgressTransition
}
