package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedSyncRetryWaitResumer @Inject constructor(
    private val syncOutboxStore: SyncOutboxStore,
    private val learningOutboxDao: LearningOutboxDao
) : SyncOutboxRetryWaitResumer {

    override suspend fun resumeRetryWaiting(now: Long) {
        syncOutboxStore.resumeRetryWaiting(now)
        learningOutboxDao.resumeRetryWaiting(now)
    }
}
