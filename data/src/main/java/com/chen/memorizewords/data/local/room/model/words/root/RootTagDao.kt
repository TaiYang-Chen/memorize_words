package com.chen.memorizewords.data.local.room.model.words.root.root

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RootTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RootTagEntity>)

    @Query("DELETE FROM root_tags WHERE root_id = :rootId")
    suspend fun deleteByRootId(rootId: Long)

    @Query("DELETE FROM root_tags WHERE root_id IN (:rootIds)")
    suspend fun deleteByRootIds(rootIds: List<Long>)

    @Query("SELECT * FROM root_tags WHERE root_id IN (:rootIds)")
    suspend fun getByRootIds(rootIds: List<Long>): List<RootTagEntity>
}
