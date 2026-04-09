package com.chen.memorizewords.data.local.room.model.words.root.rootvariant

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

/**
 * DAO for [RootVariantEntity]
 */
@Dao
interface RootVariantEntityDao {
    /**
     * Inserts multiple root variants into the database. If a root variant already exists, it will be replaced.
     *
     * @param rootVariants The root variants to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg rootVariants: RootVariantEntity)
}
