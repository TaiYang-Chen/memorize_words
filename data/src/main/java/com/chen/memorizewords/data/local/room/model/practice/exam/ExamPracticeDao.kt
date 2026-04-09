package com.chen.memorizewords.data.local.room.model.practice.exam

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExamPracticeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWordMeta(entity: ExamPracticeWordMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(entities: List<ExamPracticeItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStates(entities: List<ExamPracticeItemStateEntity>)

    @Query("SELECT * FROM exam_practice_word_meta WHERE word_id = :wordId")
    suspend fun getWordMeta(wordId: Long): ExamPracticeWordMetaEntity?

    @Query("SELECT * FROM exam_practice_item WHERE word_id = :wordId ORDER BY sort_order ASC, id ASC")
    suspend fun getItemsByWordId(wordId: Long): List<ExamPracticeItemEntity>

    @Query("SELECT * FROM exam_practice_item_state WHERE exam_item_id IN (:itemIds)")
    suspend fun getStatesByItemIds(itemIds: List<Long>): List<ExamPracticeItemStateEntity>

    @Query("DELETE FROM exam_practice_item WHERE word_id = :wordId")
    suspend fun deleteItemsByWordId(wordId: Long)
}
