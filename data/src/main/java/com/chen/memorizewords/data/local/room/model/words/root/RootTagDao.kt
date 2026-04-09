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
}
