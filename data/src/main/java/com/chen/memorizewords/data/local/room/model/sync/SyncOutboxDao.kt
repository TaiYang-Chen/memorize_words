package com.chen.memorizewords.data.local.room.model.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncOutboxEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SyncOutboxEntity>)

    @Query("SELECT * FROM sync_outbox WHERE biz_type = :bizType")
    suspend fun getByBizType(bizType: String): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE biz_type = :bizType")
    fun observeByBizType(bizType: String): Flow<List<SyncOutboxEntity>>

    @Query(
        """
        SELECT COUNT(*)
        FROM sync_outbox
        WHERE state IN ('PENDING', 'FAILED')
        """
    )
    fun observePendingCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*)
        FROM sync_outbox
        WHERE state = 'FAILED'
        """
    )
    fun observeFailedCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*)
        FROM sync_outbox
        WHERE state IN ('PENDING', 'FAILED')
        """
    )
    suspend fun getPendingCountValue(): Int

    @Query(
        """
        SELECT *
        FROM sync_outbox
        WHERE state = 'PENDING'
           OR (
                state = 'FAILED'
                AND (last_error IS NULL OR last_error NOT LIKE 'TERMINAL|%')
           )
        ORDER BY updated_at ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun getNextBatch(limit: Int): List<SyncOutboxEntity>

    @Query(
        """
        UPDATE sync_outbox
        SET state = 'SYNCING',
            updated_at = :updatedAt
        WHERE id IN (:ids)
        """
    )
    suspend fun markSyncing(ids: List<Long>, updatedAt: Long)

    @Query(
        """
        UPDATE sync_outbox
        SET state = 'FAILED',
            retry_count = retry_count + 1,
            last_error = :lastError,
            updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun markFailed(id: Long, lastError: String?, updatedAt: Long)

    @Query(
        """
        UPDATE sync_outbox
        SET state = 'PENDING',
            updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun markPending(id: Long, updatedAt: Long)

    @Query("DELETE FROM sync_outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM sync_outbox WHERE biz_key = :bizKey")
    suspend fun deleteByBizKey(bizKey: String)

    @Query("DELETE FROM sync_outbox WHERE biz_type IN (:bizTypes)")
    suspend fun deleteByBizTypes(bizTypes: List<String>)

    @Query("DELETE FROM sync_outbox")
    suspend fun deleteAll()
}
