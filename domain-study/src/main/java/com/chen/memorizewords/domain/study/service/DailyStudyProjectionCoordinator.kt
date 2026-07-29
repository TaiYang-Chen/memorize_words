package com.chen.memorizewords.domain.study.service

import com.chen.memorizewords.domain.study.model.learning.DailyPlanTargets
import com.chen.memorizewords.domain.study.model.learning.DailyProgressEvaluation
import com.chen.memorizewords.domain.study.model.learning.DailyProgressProjectionTask
import com.chen.memorizewords.domain.study.model.learning.DailyProgressTransition
import com.chen.memorizewords.domain.study.repository.record.BusinessDateProvider
import com.chen.memorizewords.domain.study.repository.record.DailyStudyProjectionQueue
import com.chen.memorizewords.domain.study.repository.record.DailyStudyProjectionStore
import com.chen.memorizewords.domain.study.repository.record.StudyRecordQuery
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class DailyStudyProjectionCoordinator @Inject constructor(
    private val writeCoordinator: DailyProgressWriteCoordinator,
    private val projectionQueue: DailyStudyProjectionQueue,
    private val projectionStore: DailyStudyProjectionStore,
    private val studyRecordQuery: StudyRecordQuery,
    private val studyPlanRepository: StudyPlanRepository,
    private val businessDateProvider: BusinessDateProvider
) {
    suspend fun projectEvent(clientEventId: String): DailyProgressTransition =
        writeCoordinator.withWrite { projectEventLocked(clientEventId) }

    suspend fun projectEventLocked(clientEventId: String): DailyProgressTransition {
        val task = projectionQueue.getById(clientEventId)
            ?: return DailyProgressTransition.RecoveryRequired(businessDateProvider.currentBusinessDate())
        return processTaskLocked(task)
    }

    suspend fun drainPending(): List<DailyProgressTransition> = writeCoordinator.withWrite {
        val transitions = mutableListOf<DailyProgressTransition>()
        val processedEventIds = mutableSetOf<String>()
        while (true) {
            val batch = projectionQueue.getPending()
                .filterNot { it.clientEventId in processedEventIds }
            if (batch.isEmpty()) break
            batch.forEach { task ->
                processedEventIds += task.clientEventId
                val transition = processTaskLocked(task)
                transitions += transition
                if (transition is DailyProgressTransition.RecoveryRequired) {
                    return@withWrite transitions
                }
            }
        }
        transitions
    }

    suspend fun reconcileCurrentDay(): DailyProgressTransition =
        writeCoordinator.withWrite { reconcileCurrentDayLocked() }

    suspend fun reconcileCurrentDayLocked(): DailyProgressTransition {
        val date = businessDateProvider.currentBusinessDate()
        val counts = studyRecordQuery.getWordCounts(date)
        val targets = (studyPlanRepository.getStudyPlan() ?: StudyPlan()).toDailyPlanTargets()
        return applySafely(
            DailyProgressEvaluation(
                businessDate = date,
                counts = counts,
                targets = targets
            )
        )
    }

    private suspend fun processTaskLocked(
        task: DailyProgressProjectionTask
    ): DailyProgressTransition {
        val transition = applySafely(
            DailyProgressEvaluation(
                businessDate = task.businessDate,
                counts = task.counts,
                targets = task.targets
            )
        )
        if (transition !is DailyProgressTransition.RecoveryRequired) {
            try {
                projectionQueue.delete(task.clientEventId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The projection is idempotent; a later drain can safely delete the replayed task.
            }
        }
        return transition
    }

    private suspend fun applySafely(
        evaluation: DailyProgressEvaluation
    ): DailyProgressTransition {
        return try {
            projectionStore.apply(evaluation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DailyProgressTransition.RecoveryRequired(evaluation.businessDate)
        }
    }
}

fun StudyPlan.toDailyPlanTargets(): DailyPlanTargets = DailyPlanTargets(
    newTarget = dailyNewCount,
    reviewTarget = dailyReviewCount
).normalized()
