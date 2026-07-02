package com.chen.memorizewords.data.sync.local.room.model.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxFailureKind
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncOutboxEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SyncOutboxEntity>)

    @Query(
        """
        SELECT *
        FROM sync_outbox
        WHERE state IN ('QUEUED', 'IN_FLIGHT', 'RETRY_WAITING', 'BLOCKED')
        """
    )
    fun observeAllPending(): Flow<List<SyncOutboxEntity>>

    @Query(
        """
        SELECT *
        FROM sync_outbox
        WHERE biz_type = :bizType
        ORDER BY updated_at ASC, id ASC
        """
    )
    suspend fun getByBizType(bizType: String): List<SyncOutboxEntity>

    @Query(
        """
        SELECT *
        FROM sync_outbox
        WHERE biz_type = :bizType
        ORDER BY updated_at ASC, id ASC
        """
    )
    fun observeByBizType(bizType: String): Flow<List<SyncOutboxEntity>>

    @Query("SELECT * FROM sync_outbox WHERE biz_key = :bizKey LIMIT 1")
    suspend fun getByBizKey(bizKey: String): SyncOutboxEntity?

    @Query("SELECT * FROM sync_outbox WHERE biz_key IN (:bizKeys)")
    suspend fun getByBizKeys(bizKeys: List<String>): List<SyncOutboxEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM sync_outbox
        WHERE state IN ('QUEUED', 'IN_FLIGHT', 'RETRY_WAITING', 'BLOCKED')
        """
    )
    fun observePendingCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*)
        FROM sync_outbox
        WHERE state IN ('QUEUED', 'IN_FLIGHT', 'RETRY_WAITING')
        """
    )
    fun observeRetryableCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*)
        FROM sync_outbox
        WHERE state = 'BLOCKED'
        """
    )
    fun observeBlockedCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*)
        FROM sync_outbox
        WHERE state IN ('QUEUED', 'IN_FLIGHT', 'RETRY_WAITING', 'BLOCKED')
        """
    )
    suspend fun getPendingCountValue(): Int

    @Query(
        """
        UPDATE sync_outbox
        SET state = 'IN_FLIGHT',
            lease_token = :leaseToken,
            lease_expires_at = :leaseExpiresAt,
            last_attempt_at = :attemptedAt,
            updated_at = :updatedAt
        WHERE id IN (
            SELECT id
            FROM sync_outbox
            WHERE state = 'QUEUED'
               OR (
                    state = 'RETRY_WAITING'
                    AND next_retry_at <= :now
               )
               OR (
                    state = 'IN_FLIGHT'
                    AND lease_expires_at > 0
                    AND lease_expires_at <= :now
               )
            ORDER BY updated_at ASC, id ASC
            LIMIT :limit
        )
        """
    )
    suspend fun claimBatch(
        now: Long,
        limit: Int,
        leaseToken: String,
        leaseExpiresAt: Long,
        attemptedAt: Long,
        updatedAt: Long
    ): Int

    @Query("SELECT * FROM sync_outbox WHERE lease_token = :leaseToken ORDER BY updated_at ASC, id ASC")
    suspend fun getByLeaseToken(leaseToken: String): List<SyncOutboxEntity>

    @Query(
        """
        UPDATE sync_outbox
        SET state = 'RETRY_WAITING',
            retry_count = retry_count + 1,
            last_error = :lastError,
            failure_kind = :failureKind,
            last_attempt_at = :lastAttemptAt,
            next_retry_at = :nextRetryAt,
            lease_token = NULL,
            lease_expires_at = 0,
            updated_at = :updatedAt
        WHERE id = :id AND lease_token = :leaseToken
        """
    )
    suspend fun markRetryWaiting(
        id: Long,
        leaseToken: String,
        lastError: String?,
        failureKind: SyncOutboxFailureKind,
        lastAttemptAt: Long,
        nextRetryAt: Long,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE sync_outbox
        SET next_retry_at = :now,
            updated_at = :now
        WHERE state = 'RETRY_WAITING'
          AND next_retry_at > :now
        """
    )
    suspend fun resumeRetryWaiting(now: Long): Int

    @Query(
        """
        UPDATE sync_outbox
        SET state = 'BLOCKED',
            last_error = :lastError,
            failure_kind = :failureKind,
            last_attempt_at = :lastAttemptAt,
            lease_token = NULL,
            lease_expires_at = 0,
            updated_at = :updatedAt
        WHERE id = :id AND lease_token = :leaseToken
        """
    )
    suspend fun markBlocked(
        id: Long,
        leaseToken: String,
        lastError: String?,
        failureKind: SyncOutboxFailureKind,
        lastAttemptAt: Long,
        updatedAt: Long
    ): Int

    @Query("DELETE FROM sync_outbox WHERE id = :id AND lease_token = :leaseToken")
    suspend fun deleteClaimed(id: Long, leaseToken: String): Int

    @Query("DELETE FROM sync_outbox WHERE biz_key = :bizKey")
    suspend fun deleteByBizKey(bizKey: String)

    @Query(
        """
        DELETE FROM sync_outbox
        WHERE state = 'BLOCKED'
          AND biz_type = 'WORD_BOOK_DELETE'
          AND last_error LIKE 'TERMINAL|http:400|%bookId invalid%'
        """
    )
    suspend fun deleteBlockedWordBookDeleteBookIdInvalid(): Int

    @Query("DELETE FROM sync_outbox")
    suspend fun deleteAll()
}
