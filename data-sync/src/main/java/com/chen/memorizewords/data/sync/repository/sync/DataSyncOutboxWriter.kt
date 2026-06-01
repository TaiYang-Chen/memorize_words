package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.domain.sync.OutboxCommand
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncOutboxWriter @Inject constructor(
    private val syncOutboxStore: SyncOutboxStore,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler
) : SyncOutboxWriter {

    override suspend fun enqueueLatest(command: OutboxCommand) {
        syncOutboxStore.enqueueLatest(
            bizType = command.topic,
            bizKey = command.key,
            operation = command.operation.toDataOperation(),
            payload = command.payload,
            updatedAt = command.updatedAtEpochMillis
        )
        syncOutboxWorkScheduler.scheduleDrain()
    }

    private fun SyncOperation.toDataOperation(): SyncOutboxOperation {
        return when (this) {
            SyncOperation.UPSERT -> SyncOutboxOperation.UPSERT
            SyncOperation.DELETE -> SyncOutboxOperation.DELETE
        }
    }
}
