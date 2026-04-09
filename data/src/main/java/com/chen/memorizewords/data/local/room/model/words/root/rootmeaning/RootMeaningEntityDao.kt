package com.chen.memorizewords.data.local.room.model.words.root.rootmeaning

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

/**
 * DAO for [RootMeaningEntity]
 */
@Dao
interface RootMeaningEntityDao {
    /**
     * Inserts multiple root meanings into the database. If a root meaning already exists, it will be replaced.
     *
     * @param rootMeanings The root meanings to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg rootMeanings: RootMeaningEntity)
}
