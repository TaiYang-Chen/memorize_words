package com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrentWordBookSelectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CurrentWordBookSelectionEntity)

    @Query("SELECT * FROM current_word_book_selection WHERE selection_id = :selectionId LIMIT 1")
    suspend fun getById(selectionId: Int = CurrentWordBookSelectionEntity.SELECTION_ID): CurrentWordBookSelectionEntity?

    @Query("SELECT * FROM current_word_book_selection WHERE selection_id = :selectionId LIMIT 1")
    fun observeById(selectionId: Int = CurrentWordBookSelectionEntity.SELECTION_ID): Flow<CurrentWordBookSelectionEntity?>

    @Query("DELETE FROM current_word_book_selection")
    suspend fun deleteAll()
}
