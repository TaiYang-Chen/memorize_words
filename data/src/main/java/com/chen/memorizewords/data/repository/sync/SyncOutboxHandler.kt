package com.chen.memorizewords.data.repository.sync

import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxEntity

interface SyncOutboxHandler {
    val bizTypes: Set<String>

    suspend fun handle(entity: SyncOutboxEntity)

    suspend fun onSuccess(entity: SyncOutboxEntity) = Unit
}
