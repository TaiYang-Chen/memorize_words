package com.chen.memorizewords.domain.floating.repository

import com.chen.memorizewords.domain.floating.model.PendingFloatingActivation
import kotlinx.coroutines.flow.Flow

interface FloatingActivationStateRepository {
    fun observePending(): Flow<PendingFloatingActivation?>
    suspend fun getPending(): PendingFloatingActivation?
    suspend fun savePending(pending: PendingFloatingActivation)
    /**
     * Clears the pending request only when [requestId] still matches the persisted request.
     *
     * Implementations shared by multiple processes must perform the compare-and-clear atomically.
     * A `null` request id is an explicit unconditional clear.
     *
     * @return `true` when the persisted value was cleared, `false` when a newer request won.
     */
    suspend fun clearPending(requestId: String? = null): Boolean
}
