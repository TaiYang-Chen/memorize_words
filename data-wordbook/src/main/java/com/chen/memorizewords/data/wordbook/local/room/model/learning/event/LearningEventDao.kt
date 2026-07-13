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

    @Query("SELECT * FROM learning_event WHERE client_event_id = :clientEventId LIMIT 1")
    suspend fun getById(clientEventId: String): LearningEventEntity?

    @Query(
        """
        SELECT COUNT(*)
        FROM learning_event
        WHERE book_id = :bookId
          AND client_sequence > :clientSequence
          AND synced_at_ms IS NULL
        """
    )
    suspend fun countPendingAfter(bookId: Long, clientSequence: Long): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM learning_event
        WHERE book_id = :bookId
          AND word_id = :wordId
          AND client_sequence > :clientSequence
          AND synced_at_ms IS NULL
        """
    )
    suspend fun countPendingForWordAfter(
        bookId: Long,
        wordId: Long,
        clientSequence: Long
    ): Int

    @Query("UPDATE learning_event SET server_state_revision = :serverRevision, synced_at_ms = :syncedAt WHERE client_event_id = :clientEventId")
    suspend fun markSynced(clientEventId: String, serverRevision: Long, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM learning_event WHERE synced_at_ms IS NULL")
    suspend fun countPending(): Int
}
