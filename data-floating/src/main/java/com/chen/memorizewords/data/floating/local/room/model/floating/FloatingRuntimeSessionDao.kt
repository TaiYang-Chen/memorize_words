package com.chen.memorizewords.data.floating.local.room.model.floating

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FloatingRuntimeSessionDao {
    @Query("SELECT * FROM floating_runtime_session WHERE singleton_id = :singletonId LIMIT 1")
    fun observe(singletonId: Int = FloatingRuntimeSessionEntity.SINGLETON_ID): Flow<FloatingRuntimeSessionEntity?>

    @Query("SELECT * FROM floating_runtime_session WHERE singleton_id = :singletonId LIMIT 1")
    suspend fun get(singletonId: Int = FloatingRuntimeSessionEntity.SINGLETON_ID): FloatingRuntimeSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FloatingRuntimeSessionEntity)

    @Query("DELETE FROM floating_runtime_session WHERE singleton_id = :singletonId")
    suspend fun clear(singletonId: Int = FloatingRuntimeSessionEntity.SINGLETON_ID)
}
