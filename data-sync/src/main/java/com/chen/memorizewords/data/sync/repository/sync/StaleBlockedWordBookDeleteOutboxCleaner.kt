package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaleBlockedWordBookDeleteOutboxCleaner @Inject constructor(
    private val syncOutboxDao: SyncOutboxDao
) {
    suspend fun clean() {
        syncOutboxDao.deleteBlockedWordBookDeleteBookIdInvalid()
    }
}
