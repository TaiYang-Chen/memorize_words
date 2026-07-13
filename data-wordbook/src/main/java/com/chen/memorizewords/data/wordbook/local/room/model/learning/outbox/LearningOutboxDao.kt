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
        SELECT o.*
        FROM learning_outbox o
        LEFT JOIN learning_event e ON e.client_event_id = o.client_event_id
        WHERE o.status = :status
        ORDER BY e.client_sequence IS NULL ASC,
                 e.client_sequence ASC,
                 o.created_at_ms ASC
        LIMIT :limit
        """
    )
    suspend fun getByStatus(status: String = LearningOutboxEntity.STATUS_PENDING, limit: Int = 50): List<LearningOutboxEntity>

    @Query(
        """
        SELECT *
        FROM learning_outbox
        WHERE status IN ('PENDING', 'SYNCING', 'BLOCKED')
        ORDER BY updated_at_ms DESC, client_event_id DESC
        """
    )
    fun observeAllPending(): Flow<List<LearningOutboxEntity>>

    @Query(
        """
        SELECT o.*
        FROM learning_outbox o
        LEFT JOIN learning_event e ON e.client_event_id = o.client_event_id
        WHERE o.status = :pendingStatus
          AND o.next_retry_at_ms <= :now
          AND o.client_event_id = (
              SELECT previous.client_event_id
              FROM learning_outbox previous
              LEFT JOIN learning_event previous_event
                ON previous_event.client_event_id = previous.client_event_id
              WHERE previous.book_id = o.book_id
                AND previous.status IN ('PENDING', 'SYNCING', 'BLOCKED')
              ORDER BY previous_event.client_sequence IS NULL ASC,
                       previous_event.client_sequence ASC,
                       previous.created_at_ms ASC,
                       previous.client_event_id ASC
              LIMIT 1
          )
        ORDER BY e.client_sequence IS NULL ASC,
                 e.client_sequence ASC,
                 o.created_at_ms ASC
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
            lease_until_at_ms = :leaseUntilAt,
            last_attempt_at_ms = :now,
            updated_at_ms = :now
        WHERE client_event_id = :clientEventId
          AND status = :pendingStatus
          AND next_retry_at_ms <= :now
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
            updated_at_ms = :updatedAtMs,
            last_error = NULL,
            lease_token = NULL,
            lease_until_at_ms = NULL
        WHERE client_event_id = :clientEventId
        """
    )
    suspend fun markStatus(clientEventId: String, status: String, updatedAtMs: Long)

    @Query(
        """
        UPDATE learning_outbox
        SET status = :pendingStatus,
            attempt_count = attempt_count + 1,
            last_error = :error,
            next_retry_at_ms = :nextRetryAt,
            lease_token = NULL,
            lease_until_at_ms = NULL,
            updated_at_ms = :updatedAtMs
        WHERE client_event_id = :clientEventId
          AND lease_token = :leaseToken
        """
    )
    suspend fun markRetryPending(
        clientEventId: String,
        leaseToken: String,
        error: String,
        nextRetryAt: Long,
        updatedAtMs: Long,
        pendingStatus: String = LearningOutboxEntity.STATUS_PENDING
    )

    @Query(
        """
        UPDATE learning_outbox
        SET next_retry_at_ms = :now,
            updated_at_ms = :now
        WHERE status = 'PENDING'
          AND next_retry_at_ms > :now
          AND (
               last_error LIKE 'RETRY|io|%'
            OR last_error LIKE 'RETRY|auth|%'
            OR last_error LIKE 'RETRY|http:5%'
            OR last_error LIKE 'RETRY|unknown%'
          )
        """
    )
    suspend fun resumeRetryWaiting(now: Long): Int

    @Query(
        """
        UPDATE learning_outbox
        SET status = :blockedStatus,
            attempt_count = attempt_count + 1,
            last_error = :error,
            lease_token = NULL,
            lease_until_at_ms = NULL,
            updated_at_ms = :updatedAtMs
        WHERE client_event_id = :clientEventId
          AND lease_token = :leaseToken
        """
    )
    suspend fun markBlocked(
        clientEventId: String,
        leaseToken: String,
        error: String,
        updatedAtMs: Long,
        blockedStatus: String = LearningOutboxEntity.STATUS_BLOCKED
    )

    @Query(
        """
        UPDATE learning_outbox
        SET status = :pendingStatus,
            lease_token = NULL,
            lease_until_at_ms = NULL,
            updated_at_ms = :now
        WHERE status = :syncingStatus
          AND lease_until_at_ms <= :now
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

    @Query(
        """
        SELECT MIN(next_retry_at_ms)
        FROM learning_outbox
        WHERE status = 'PENDING'
          AND next_retry_at_ms > :now
        """
    )
    suspend fun getEarliestRetryAt(now: Long): Long?

}
