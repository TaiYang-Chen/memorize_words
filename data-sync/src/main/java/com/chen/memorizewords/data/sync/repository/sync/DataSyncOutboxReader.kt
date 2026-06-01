package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.domain.sync.OutboxRecord
import com.chen.memorizewords.domain.sync.SyncOutboxReader
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataSyncOutboxReader @Inject constructor(
    private val syncOutboxDao: SyncOutboxDao
) : SyncOutboxReader {
    override fun observeByTopic(topic: String): Flow<List<OutboxRecord>> {
        return syncOutboxDao.observeByBizType(topic).map { entities ->
            entities.map { it.toDomainRecord() }
        }
    }

    override suspend fun getByTopic(topic: String): List<OutboxRecord> {
        return syncOutboxDao.getByBizType(topic).map { it.toDomainRecord() }
    }
}
