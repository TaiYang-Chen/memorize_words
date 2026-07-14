package com.chen.memorizewords.data.sync.local.room.model.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FailedSyncEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: FailedSyncEventEntity)

    @Query(
        """
        SELECT * FROM failed_sync_event
        WHERE dedupe_key = :dedupeKey
          AND state IN ('PENDING', 'RETRY_WAITING', 'BLOCKED')
        ORDER BY updated_at_ms DESC
        LIMIT 1
        """
    )
    suspend fun findMergeable(dedupeKey: String): FailedSyncEventEntity?

    @Query(
        """
        UPDATE failed_sync_event
        SET event_type = :eventType,
            schema_version = :schemaVersion,
            delivery_mode = :deliveryMode,
            ordering_key = :orderingKey,
            sequence = :sequence,
            params_json = :paramsJson,
            state = 'PENDING',
            attempt_count = 0,
            last_error = NULL,
            next_attempt_at_ms = :now,
            occurred_at_ms = :occurredAtMs,
            updated_at_ms = :now
        WHERE event_id = :eventId
          AND state IN ('PENDING', 'RETRY_WAITING', 'BLOCKED')
        """
    )
    suspend fun replaceMergeable(
        eventId: String,
        eventType: String,
        schemaVersion: Int,
        deliveryMode: FailedSyncDeliveryMode,
        orderingKey: String,
        sequence: Long?,
        paramsJson: String,
        occurredAtMs: Long,
        now: Long
    ): Int

    @Query(
        """
        UPDATE failed_sync_event
        SET state = 'IN_FLIGHT',
            lease_token = :leaseToken,
            lease_expires_at_ms = :leaseExpiresAtMs,
            updated_at_ms = :now
        WHERE event_id IN (
            SELECT event_id FROM failed_sync_event candidate
            WHERE (
                    state = 'PENDING'
                 OR (state = 'RETRY_WAITING' AND next_attempt_at_ms <= :now)
                 OR (state = 'IN_FLIGHT' AND lease_expires_at_ms <= :now)
              )
              AND (
                    sequence IS NULL
                 OR NOT EXISTS (
                     SELECT 1 FROM failed_sync_event previous
                     WHERE previous.ordering_key = candidate.ordering_key
                       AND previous.sequence IS NOT NULL
                       AND previous.sequence < candidate.sequence
                       AND previous.state IN ('PENDING', 'IN_FLIGHT', 'RETRY_WAITING', 'BLOCKED')
                 )
              )
            ORDER BY occurred_at_ms ASC, created_at_ms ASC
            LIMIT :limit
        )
        """
    )
    suspend fun claimDue(
        limit: Int,
        leaseToken: String,
        leaseExpiresAtMs: Long,
        now: Long
    ): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM failed_sync_event candidate
            WHERE (
                    state = 'PENDING'
                 OR (state = 'RETRY_WAITING' AND next_attempt_at_ms <= :now)
                 OR (state = 'IN_FLIGHT' AND lease_expires_at_ms <= :now)
              )
              AND (
                    sequence IS NULL
                 OR NOT EXISTS (
                     SELECT 1 FROM failed_sync_event previous
                     WHERE previous.ordering_key = candidate.ordering_key
                       AND previous.sequence IS NOT NULL
                       AND previous.sequence < candidate.sequence
                       AND previous.state IN ('PENDING', 'IN_FLIGHT', 'RETRY_WAITING', 'BLOCKED')
                 )
              )
            LIMIT 1
        )
        """
    )
    suspend fun hasClaimable(now: Long): Boolean

    @Query("SELECT * FROM failed_sync_event WHERE lease_token = :leaseToken ORDER BY occurred_at_ms ASC")
    suspend fun getByLeaseToken(leaseToken: String): List<FailedSyncEventEntity>

    @Query("DELETE FROM failed_sync_event WHERE event_id = :eventId AND lease_token = :leaseToken")
    suspend fun deleteClaimed(eventId: String, leaseToken: String): Int

    @Query(
        "SELECT EXISTS(SELECT 1 FROM failed_sync_event WHERE event_id = :eventId AND lease_token = :leaseToken AND state = 'IN_FLIGHT')"
    )
    suspend fun isClaimed(eventId: String, leaseToken: String): Boolean

    @Query(
        """
        UPDATE failed_sync_event
        SET state = 'RETRY_WAITING',
            attempt_count = attempt_count + 1,
            last_error = :lastError,
            next_attempt_at_ms = :nextAttemptAtMs,
            lease_token = NULL,
            lease_expires_at_ms = 0,
            updated_at_ms = :now
        WHERE event_id = :eventId AND lease_token = :leaseToken
        """
    )
    suspend fun markRetryWaiting(
        eventId: String,
        leaseToken: String,
        lastError: String,
        nextAttemptAtMs: Long,
        now: Long
    ): Int

    @Query(
        """
        UPDATE failed_sync_event
        SET state = 'BLOCKED',
            last_error = :lastError,
            lease_token = NULL,
            lease_expires_at_ms = 0,
            updated_at_ms = :now
        WHERE event_id = :eventId AND lease_token = :leaseToken
        """
    )
    suspend fun markBlocked(
        eventId: String,
        leaseToken: String,
        lastError: String,
        now: Long
    ): Int

    @Query(
        """
        UPDATE failed_sync_event
        SET state = 'RETRY_WAITING',
            lease_token = NULL,
            lease_expires_at_ms = 0,
            next_attempt_at_ms = :nextAttemptAtMs,
            updated_at_ms = :now
        WHERE lease_token = :leaseToken AND state = 'IN_FLIGHT'
        """
    )
    suspend fun releaseLease(leaseToken: String, nextAttemptAtMs: Long, now: Long): Int

    @Query(
        """
        UPDATE failed_sync_event
        SET next_attempt_at_ms = :now,
            updated_at_ms = :now
        WHERE state = 'RETRY_WAITING'
          AND next_attempt_at_ms > :now
        """
    )
    suspend fun resumeRetryWaiting(now: Long): Int

    @Query(
        """
        SELECT MIN(
            CASE
                WHEN candidate.state = 'RETRY_WAITING' THEN candidate.next_attempt_at_ms
                WHEN candidate.state = 'IN_FLIGHT' THEN candidate.lease_expires_at_ms
            END
        )
        FROM failed_sync_event candidate
        WHERE (
                (candidate.state = 'RETRY_WAITING' AND candidate.next_attempt_at_ms > :now)
             OR (candidate.state = 'IN_FLIGHT' AND candidate.lease_expires_at_ms > :now)
          )
          AND (
                candidate.sequence IS NULL
             OR NOT EXISTS (
                 SELECT 1 FROM failed_sync_event previous
                 WHERE previous.ordering_key = candidate.ordering_key
                   AND previous.sequence IS NOT NULL
                   AND previous.sequence < candidate.sequence
                   AND previous.state IN ('PENDING', 'IN_FLIGHT', 'RETRY_WAITING', 'BLOCKED')
             )
          )
        """
    )
    suspend fun earliestRetryAt(now: Long): Long?

    @Query("SELECT COUNT(*) FROM failed_sync_event WHERE state != 'BLOCKED'")
    suspend fun retryableCount(): Int

    @Query(
        "SELECT COUNT(*) FROM failed_sync_event WHERE state IN ('PENDING','IN_FLIGHT','RETRY_WAITING','BLOCKED')"
    )
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM failed_sync_event WHERE state IN ('PENDING','IN_FLIGHT','RETRY_WAITING','BLOCKED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM failed_sync_event WHERE state IN ('PENDING','IN_FLIGHT','RETRY_WAITING')")
    fun observeRetryableCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM failed_sync_event WHERE state = 'BLOCKED'")
    fun observeBlockedCount(): Flow<Int>

    @Query("SELECT * FROM failed_sync_event WHERE state IN ('PENDING','IN_FLIGHT','RETRY_WAITING','BLOCKED') ORDER BY updated_at_ms DESC")
    fun observeAllPending(): Flow<List<FailedSyncEventEntity>>

    @Query(
        """
        SELECT * FROM failed_sync_event
        WHERE event_type = :eventType
          AND state IN ('PENDING','IN_FLIGHT','RETRY_WAITING','BLOCKED')
        """
    )
    suspend fun getPendingByEventType(eventType: String): List<FailedSyncEventEntity>

    @Query(
        """
        SELECT * FROM failed_sync_event
        WHERE event_type = :eventType
          AND state IN ('PENDING','IN_FLIGHT','RETRY_WAITING','BLOCKED')
        """
    )
    fun observePendingByEventType(eventType: String): Flow<List<FailedSyncEventEntity>>

    @Query("DELETE FROM failed_sync_event")
    suspend fun deleteAll()
}
