package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningEventRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningEventResultDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningProgressDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningWordStateDto
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxEntity
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.learning.LearningEventSyncResultSnapshot
import com.chen.memorizewords.domain.study.repository.learning.LearningSyncStatePort
import com.chen.memorizewords.domain.sync.LearningEventSyncPayload
import com.chen.memorizewords.domain.sync.SyncConflictPolicy
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress
import com.google.gson.Gson
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningOutboxProcessor @Inject constructor(
    private val learningOutboxDao: LearningOutboxDao,
    private val remoteLearningSyncDataSource: RemoteLearningSyncDataSource,
    private val learningSyncStatePort: LearningSyncStatePort,
    private val conflictPolicy: SyncConflictPolicy,
    private val gson: Gson
) {
    suspend fun drainBatch(limit: Int = DEFAULT_BATCH_SIZE): SyncOutboxProcessor.DrainResult {
        val now = System.currentTimeMillis()
        learningOutboxDao.releaseExpiredLeases(now)
        val claimable = learningOutboxDao.getClaimable(now = now, limit = limit)
        if (claimable.isEmpty()) {
            return SyncOutboxProcessor.DrainResult.Empty
        }

        var shouldRetry = false
        claimable.forEach { entity ->
            val leaseToken = UUID.randomUUID().toString()
            val claimed = learningOutboxDao.markSyncing(
                clientEventId = entity.clientEventId,
                leaseToken = leaseToken,
                leaseUntilAt = now + LEASE_DURATION_MS,
                now = now
            )
            if (claimed != 1) return@forEach

            val result = runCatching {
                val response = remoteLearningSyncDataSource
                    .recordLearningEvent(gson.fromJson(entity.payload, LearningEventSyncPayload::class.java).toRequest())
                    .getOrThrow()
                learningSyncStatePort.applyLearningEventSyncResult(response.toSnapshot())
            }
            if (result.isSuccess) {
                learningOutboxDao.deleteClaimed(entity.clientEventId, leaseToken)
            } else {
                val failure = conflictPolicy.decide(result.exceptionOrNull())
                val attemptTime = System.currentTimeMillis()
                if (failure.shouldRetry) {
                    learningOutboxDao.markRetryPending(
                        clientEventId = entity.clientEventId,
                        leaseToken = leaseToken,
                        error = failure.persistedMessage,
                        nextRetryAt = attemptTime + syncOutboxBackoffDelayMillis(entity.attemptCount + 1),
                        updatedAtMs = attemptTime
                    )
                    shouldRetry = true
                } else {
                    learningOutboxDao.markBlocked(
                        clientEventId = entity.clientEventId,
                        leaseToken = leaseToken,
                        error = failure.persistedMessage,
                        updatedAtMs = attemptTime
                    )
                }
            }
        }

        return if (shouldRetry) {
            SyncOutboxProcessor.DrainResult.RetryNeeded
        } else {
            SyncOutboxProcessor.DrainResult.Drained
        }
    }

    private fun LearningEventSyncPayload.toRequest(): LearningEventRequest {
        return LearningEventRequest(
            clientEventId = clientEventId,
            deviceId = deviceId,
            clientSequence = clientSequence,
            bookId = bookId,
            wordId = wordId,
            action = action,
            quality = quality,
            correct = correct,
            businessDate = businessDate,
            occurredAtMs = occurredAt,
            baseStateRevision = baseStateRevision,
            payloadJson = payloadJson,
            schemaVersion = schemaVersion
        )
    }

    private fun LearningEventResultDto.toSnapshot(): LearningEventSyncResultSnapshot {
        val progress = learningProgress ?: wordBookProgress
        return LearningEventSyncResultSnapshot(
            clientEventId = clientEventId,
            conflict = conflict,
            wordState = wordState?.toDomain(),
            learningProgress = progress?.toDomain(),
            serverStateRevision = wordState?.stateRevision ?: 0L
        )
    }

    private fun LearningWordStateDto.toDomain(): WordLearningState {
        return WordLearningState(
            wordId = wordId,
            bookId = bookId,
            totalLearnCount = totalLearnCount,
            lastLearnedAtMs = lastLearnedAtMs,
            nextReviewAtMs = nextReviewAtMs,
            masteryLevel = masteryLevel,
            userStatus = userStatus,
            repetition = repetition,
            interval = interval,
            efactor = efactor,
            stateRevision = stateRevision
        )
    }

    private fun LearningProgressDto.toDomain(): WordBookProgress {
        return WordBookProgress(
            wordBookId = bookId,
            wordBookName = bookName,
            learningCount = learnedCount,
            masteredCount = masteredCount,
            totalCount = totalCount,
            correctCount = correctCount,
            wrongCount = wrongCount,
            studyDayCount = studyDayCount,
            lastStudyDate = lastStudyDate.orEmpty(),
            revision = revision
        )
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 20
        const val LEASE_DURATION_MS = 2 * 60_000L
    }
}
