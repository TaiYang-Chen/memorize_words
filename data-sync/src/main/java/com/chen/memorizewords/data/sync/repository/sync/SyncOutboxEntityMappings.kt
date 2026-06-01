package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxEntity
import com.chen.memorizewords.domain.sync.OutboxRecord
import com.chen.memorizewords.domain.sync.SyncOperation

internal fun SyncOutboxEntity.toDomainRecord(): OutboxRecord {
    return OutboxRecord(
        id = id.toString(),
        aggregate = bizType,
        key = bizKey,
        operation = when (operation) {
            SyncOutboxOperation.UPSERT -> SyncOperation.UPSERT
            SyncOutboxOperation.DELETE -> SyncOperation.DELETE
        },
        payload = payload,
        createdAtEpochMillis = updatedAt
    )
}
