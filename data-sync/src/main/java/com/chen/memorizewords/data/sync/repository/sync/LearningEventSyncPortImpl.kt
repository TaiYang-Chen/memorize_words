package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.data.sync.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningEventRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningEventResultDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningProgressDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningWordStateDto
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.learning.BookLearningWriteCoordinator
import com.chen.memorizewords.domain.study.repository.learning.LearningEventSyncResultSnapshot
import com.chen.memorizewords.domain.study.repository.learning.LearningSyncStatePort
import com.chen.memorizewords.domain.study.repository.sync.LearningEventSyncPort
import com.chen.memorizewords.domain.sync.LearningEventSyncPayload
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress
import javax.inject.Inject

class LearningEventSyncPortImpl @Inject constructor(
    private val remote: RemoteLearningSyncDataSource,
    private val launcher: DirectSyncLauncher,
    private val writeCoordinator: BookLearningWriteCoordinator,
    private val syncStatePort: LearningSyncStatePort
) : LearningEventSyncPort {
    override fun schedule(payload: LearningEventSyncPayload) {
        launcher.launch(
            operation = "learning_event",
            orderingKey = "learning:${payload.bookId}",
            request = { remote.recordLearningEvent(payload.toRequest()) },
            onSuccess = { response ->
                writeCoordinator.withBookWrite(payload.bookId) {
                    syncStatePort.applyLearningEventSyncResult(response.toSnapshot())
                }
            }
        )
    }
}

private fun LearningEventSyncPayload.toRequest(): LearningEventRequest = LearningEventRequest(
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

private fun LearningWordStateDto.toDomain(): WordLearningState = WordLearningState(
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

private fun LearningProgressDto.toDomain(): WordBookProgress = WordBookProgress(
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
