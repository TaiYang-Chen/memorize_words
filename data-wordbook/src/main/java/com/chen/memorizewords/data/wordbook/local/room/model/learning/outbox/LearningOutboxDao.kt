package com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LearningOutboxEntity)

    @Query(
        """
        SELECT *
        FROM learning_outbox
        WHERE status = :status
        ORDER BY updated_at ASC
        LIMIT :limit
        """
    )
    suspend fun getByStatus(status: String = LearningOutboxEntity.STATUS_PENDING, limit: Int = 50): List<LearningOutboxEntity>

    @Query(
        """
        SELECT *
        FROM learning_outbox
        WHERE status = :pendingStatus
          AND next_retry_at <= :now
        ORDER BY updated_at ASC
        LIMIT :limit
        """
    )
    suspend fun getClaimable(
        now: Long,
        limit: Int,
        pendingStatus: String = LearningOutboxEntity.STATUS_PENDING
    ): List<LearningOutboxEntity>

    @Query(
        """
        UPDATE learning_outbox
        SET status = :syncingStatus,
            lease_token = :leaseToken,
            lease_until_at = :leaseUntilAt,
            last_attempt_at = :now,
            updated_at = :now
        WHERE client_event_id = :clientEventId
          AND status = :pendingStatus
          AND next_retry_at <= :now
        """
    )
    suspend fun markSyncing(
        clientEventId: String,
        leaseToken: String,
        leaseUntilAt: Long,
        now: Long,
        pendingStatus: String = LearningOutboxEntity.STATUS_PENDING,
        syncingStatus: String = LearningOutboxEntity.STATUS_SYNCING
    ): Int

    @Query(
        """
        UPDATE learning_outbox
        SET status = :status,
            updated_at = :updatedAt,
            last_error = NULL,
            lease_token = NULL,
            lease_until_at = NULL
        WHERE client_event_id = :clientEventId
        """
    )
    suspend fun markStatus(clientEventId: String, status: String, updatedAt: Long)

    @Query(
        """
        UPDATE learning_outbox
        SET status = :pendingStatus,
            attempt_count = attempt_count + 1,
            last_error = :error,
            next_retry_at = :nextRetryAt,
            lease_token = NULL,
            lease_until_at = NULL,
            updated_at = :updatedAt
        WHERE client_event_id = :clientEventId
          AND lease_token = :leaseToken
        """
    )
    suspend fun markRetryPending(
        clientEventId: String,
        leaseToken: String,
        error: String,
        nextRetryAt: Long,
        updatedAt: Long,
        pendingStatus: String = LearningOutboxEntity.STATUS_PENDING
    )

    @Query(
        """
        UPDATE learning_outbox
        SET status = :blockedStatus,
            attempt_count = attempt_count + 1,
            last_error = :error,
            lease_token = NULL,
            lease_until_at = NULL,
            updated_at = :updatedAt
        WHERE client_event_id = :clientEventId
          AND lease_token = :leaseToken
        """
    )
    suspend fun markBlocked(
        clientEventId: String,
        leaseToken: String,
        error: String,
        updatedAt: Long,
        blockedStatus: String = LearningOutboxEntity.STATUS_BLOCKED
    )

    @Query(
        """
        UPDATE learning_outbox
        SET status = :pendingStatus,
            lease_token = NULL,
            lease_until_at = NULL,
            updated_at = :now
        WHERE status = :syncingStatus
          AND lease_until_at <= :now
        """
    )
    suspend fun releaseExpiredLeases(
        now: Long,
        pendingStatus: String = LearningOutboxEntity.STATUS_PENDING,
        syncingStatus: String = LearningOutboxEntity.STATUS_SYNCING
    )

    @Query("DELETE FROM learning_outbox WHERE client_event_id = :clientEventId AND lease_token = :leaseToken")
    suspend fun deleteClaimed(clientEventId: String, leaseToken: String)

    @Query("SELECT COUNT(*) FROM learning_outbox WHERE status = :status")
    suspend fun countByStatus(status: String = LearningOutboxEntity.STATUS_PENDING): Int

    @Query("SELECT COUNT(*) FROM learning_outbox WHERE status IN (:statuses)")
    suspend fun countByStatuses(statuses: List<String>): Int

    @Query("SELECT COUNT(*) FROM learning_outbox WHERE status IN (:statuses)")
    fun observeCountByStatuses(statuses: List<String>): Flow<Int>

    @Query("SELECT COUNT(*) FROM learning_outbox WHERE status = :status")
    fun observeCountByStatus(status: String): Flow<Int>
}
