package com.chen.memorizewords.domain.sync

interface ServerBootstrapContributor {
    val bootstrapKey: String
    suspend fun bootstrapFromServer(): Result<Unit>
}

data class LearningEventSyncPayload(
    val schemaVersion: Int = 1,
    val clientEventId: String,
    val deviceId: String?,
    val clientSequence: Long,
    val bookId: Long,
    val wordId: Long,
    val action: String,
    val quality: Int?,
    val correct: Boolean?,
    val businessDate: String,
    val occurredAt: Long,
    val baseStateRevision: Long,
    val payloadJson: String?
)
