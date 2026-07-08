package com.chen.memorizewords.data.wordbook.repository.learning

import com.chen.memorizewords.data.wordbook.local.room.model.learning.event.LearningEventDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxEntity
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.toEntity
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressEntity
import com.chen.memorizewords.data.wordbook.repository.WordBookTransactionRunner
import com.chen.memorizewords.domain.study.repository.learning.LearningEventSyncResultSnapshot
import com.chen.memorizewords.domain.study.repository.learning.LearningSyncStatePort
import javax.inject.Inject

class LearningSyncStateRepository @Inject constructor(
    private val transactionRunner: WordBookTransactionRunner,
    private val learningEventDao: LearningEventDao,
    private val learningOutboxDao: LearningOutboxDao,
    private val wordLearningStateDao: WordLearningStateDao,
    private val wordBookProgressDao: WordBookProgressDao
) : LearningSyncStatePort {

    override suspend fun hasPendingLearningEvents(): Boolean {
        return learningEventDao.countPending() > 0 ||
            learningOutboxDao.countByStatuses(
                listOf(
                    LearningOutboxEntity.STATUS_PENDING,
                    LearningOutboxEntity.STATUS_SYNCING,
                    LearningOutboxEntity.STATUS_BLOCKED
                )
            ) > 0
    }

    override suspend fun applyLearningEventSyncResult(result: LearningEventSyncResultSnapshot) {
        check(!result.conflict) { "learning event conflict: ${result.clientEventId}" }
        transactionRunner.runInTransaction {
            result.wordState?.let { state ->
                wordLearningStateDao.upsert(state.toEntity())
            }
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
            learningEventDao.markSynced(
                clientEventId = result.clientEventId,
                serverRevision = result.serverStateRevision,
                syncedAt = System.currentTimeMillis()
            )
        }
    }
}
