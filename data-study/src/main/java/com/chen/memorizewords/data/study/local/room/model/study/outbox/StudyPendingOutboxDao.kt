package com.chen.memorizewords.data.study.local.room.model.study.outbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StudyPendingOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StudyPendingOutboxEntity>)

    @Query("SELECT * FROM study_pending_outbox ORDER BY updated_at_ms ASC, id ASC")
    suspend fun getAll(): List<StudyPendingOutboxEntity>

    @Query("DELETE FROM study_pending_outbox WHERE biz_key IN (:bizKeys)")
    suspend fun deleteByBizKeys(bizKeys: List<String>)

    @Query("DELETE FROM study_pending_outbox")
    suspend fun deleteAll()
}
