package com.chen.memorizewords.data.wordbook.repository.learning

import com.chen.memorizewords.data.wordbook.local.room.model.learning.projection.DailyProgressProjectionTaskDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.projection.DailyProgressProjectionTaskEntity
import com.chen.memorizewords.domain.study.model.learning.DailyPlanTargets
import com.chen.memorizewords.domain.study.model.learning.DailyProgressProjectionTask
import com.chen.memorizewords.domain.study.model.learning.DailyWordCounts
import com.chen.memorizewords.domain.study.repository.record.DailyStudyProjectionQueue
import javax.inject.Inject

class DailyStudyProjectionQueueImpl @Inject constructor(
    private val dao: DailyProgressProjectionTaskDao
) : DailyStudyProjectionQueue {
    override suspend fun getById(clientEventId: String): DailyProgressProjectionTask? =
        dao.getById(clientEventId)?.toDomain()

    override suspend fun getPending(limit: Int): List<DailyProgressProjectionTask> =
        dao.getPending(limit).map(DailyProgressProjectionTaskEntity::toDomain)

    override suspend fun delete(clientEventId: String) {
        dao.delete(clientEventId)
    }
}

private fun DailyProgressProjectionTaskEntity.toDomain(): DailyProgressProjectionTask =
    DailyProgressProjectionTask(
        clientEventId = clientEventId,
        clientSequence = clientSequence,
        businessDate = businessDate,
        counts = DailyWordCounts(
            newCount = newCountAfter,
            reviewCount = reviewCountAfter
        ),
        targets = DailyPlanTargets(
            newTarget = dailyNewTarget,
            reviewTarget = dailyReviewTarget
        ),
        createdAtMs = createdAtMs
    )
