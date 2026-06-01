package com.chen.memorizewords.domain.sync
import kotlinx.coroutines.flow.Flow

enum class SyncOperation {
    UPSERT,
    DELETE
}

data class OutboxRecord(
    val id: String,
    val aggregate: String,
    val key: String,
    val operation: SyncOperation,
    val payload: String,
    val createdAtEpochMillis: Long
)

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Pending(val count: Int) : SyncStatus
    data class Failed(val reason: String) : SyncStatus
}

interface SyncRepository {
    fun observeStatus(): Flow<SyncStatus>
    suspend fun enqueue(record: OutboxRecord)
    suspend fun drain(): Result<Unit>
    suspend fun bootstrapFromServer(): Result<Unit>
}
