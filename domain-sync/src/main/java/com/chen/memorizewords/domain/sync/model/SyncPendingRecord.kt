package com.chen.memorizewords.domain.sync.model
data class SyncPendingRecord(
    val id: String,
    val sourceId: String,
    val bizType: String,
    val bizKey: String,
    val operation: String,
    val payload: String,
    val state: String,
    val retryCount: Int,
    val lastError: String?,
    val failureKind: String?,
    val lastAttemptAt: Long,
    val nextRetryAt: Long,
    val updatedAtMs: Long
)
