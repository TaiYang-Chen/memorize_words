package com.chen.memorizewords.data.wordbook.local.room.model.learning.event

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LearningEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: LearningEventEntity)

    @Query("SELECT COALESCE(MAX(client_sequence), 0) FROM learning_event")
    suspend fun getMaxClientSequence(): Long

    @Query("UPDATE learning_event SET server_state_revision = :serverRevision, synced_at_ms = :syncedAt WHERE client_event_id = :clientEventId")
    suspend fun markSynced(clientEventId: String, serverRevision: Long, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM learning_event WHERE synced_at_ms IS NULL")
    suspend fun countPending(): Int
}
