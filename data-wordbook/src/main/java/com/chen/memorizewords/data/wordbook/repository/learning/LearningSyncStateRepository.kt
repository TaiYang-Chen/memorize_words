package com.chen.memorizewords.data.wordbook.repository.learning

import com.chen.memorizewords.data.wordbook.local.room.model.learning.event.LearningEventDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.toEntity
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressEntity
import com.chen.memorizewords.data.wordbook.repository.WordBookTransactionRunner
import com.chen.memorizewords.domain.study.repository.learning.LearningEventSyncResultSnapshot
import com.chen.memorizewords.domain.study.repository.learning.LearningSyncStatePort
import com.chen.memorizewords.domain.study.model.learning.LearningEventAction
import javax.inject.Inject

class LearningSyncStateRepository @Inject constructor(
    private val transactionRunner: WordBookTransactionRunner,
    private val learningEventDao: LearningEventDao,
    private val wordLearningStateDao: WordLearningStateDao,
    private val wordBookProgressDao: WordBookProgressDao
) : LearningSyncStatePort {

    override suspend fun hasPendingLearningEvents(): Boolean {
        return learningEventDao.countPending() > 0
    }

    override suspend fun applyLearningEventSyncResult(result: LearningEventSyncResultSnapshot) {
        check(!result.conflict) { "learning event conflict: ${result.clientEventId}" }
        transactionRunner.runInTransaction {
            val localEvent = learningEventDao.getById(result.clientEventId)
            val hasLaterWordEvent = localEvent?.let { event ->
                learningEventDao.countPendingForWordAfter(
                    bookId = event.bookId,
                    wordId = event.wordId,
                    clientSequence = event.clientSequence
                ) > 0
            } ?: true
            val hasLaterBookEvent = localEvent?.let { event ->
                learningEventDao.countPendingAfter(
                    bookId = event.bookId,
                    clientSequence = event.clientSequence
                ) > 0
            } ?: true

            if (!hasLaterWordEvent) {
                result.wordState?.let { state ->
                    val lastEventId = if (localEvent?.action.changesWordState()) {
                        result.clientEventId
                    } else {
                        wordLearningStateDao.getState(state.wordId, state.bookId)?.lastEventId
                    }
                    wordLearningStateDao.upsert(state.copy(lastEventId = lastEventId).toEntity())
                }
            }
            if (!hasLaterBookEvent) {
                result.learningProgress?.let { progress ->
                    wordBookProgressDao.upsert(
                        WordBookProgressEntity(
                            wordBookId = progress.wordBookId,
                            correctCount = progress.correctCount,
                            wrongCount = progress.wrongCount,
                            studyDayCount = progress.studyDayCount,
                            lastStudyDate = progress.lastStudyDate,
                            revision = progress.revision
                        )
                    )
                }
            }
            learningEventDao.markSynced(
                clientEventId = result.clientEventId,
                serverRevision = result.serverStateRevision,
                syncedAt = System.currentTimeMillis()
            )
        }
    }
}

private fun String?.changesWordState(): Boolean {
    val action = runCatching { LearningEventAction.valueOf(this.orEmpty()) }.getOrNull()
    return action != null &&
        action != LearningEventAction.SKIPPED &&
        action != LearningEventAction.ANSWER_RECORDED
}
