package com.chen.memorizewords.data.study.local.room.model.study.favorites

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WordFavoritesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WordFavoriteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WordFavoriteEntity>)

    @Query("DELETE FROM word_favorite WHERE word_id = :wordId")
    suspend fun deleteByWordId(wordId: Long): Int

    @Query("SELECT * FROM word_favorite WHERE word_id = :wordId LIMIT 1")
    suspend fun getByWordId(wordId: Long): WordFavoriteEntity?

    @Query("SELECT COUNT(*) FROM word_favorite")
    suspend fun countAll(): Int

    @Query(
        """
        SELECT
            word_id AS wordId,
            word AS word,
            definitions AS definitions,
            phonetic AS phonetic,
            CASE WHEN added_date = '' THEN date(added_at / 1000, 'unixepoch', 'localtime') ELSE added_date END AS addedDate
        FROM word_favorite
        ORDER BY added_at DESC, word_id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getPagedRows(limit: Int, offset: Int): List<FavoriteWordRow>

    @Query(
        """
        SELECT
            word_id AS wordId,
            word AS word,
            definitions AS definitions,
            phonetic AS phonetic,
            CASE WHEN added_date = '' THEN date(added_at / 1000, 'unixepoch', 'localtime') ELSE added_date END AS addedDate
        FROM word_favorite
        ORDER BY added_at DESC, word_id DESC
        """
    )
    suspend fun getAllRows(): List<FavoriteWordRow>

    @Query(
        """
        SELECT
            word_id AS wordId,
            word AS word,
            definitions AS definitions,
            phonetic AS phonetic,
            CASE WHEN added_date = '' THEN date(added_at / 1000, 'unixepoch', 'localtime') ELSE added_date END AS addedDate
        FROM word_favorite
        ORDER BY added_at DESC, word_id DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentRows(limit: Int): List<FavoriteWordRow>

    @Query(
        """
        SELECT
            word_id AS wordId,
            word AS word,
            definitions AS definitions,
            phonetic AS phonetic,
            CASE WHEN added_date = '' THEN date(added_at / 1000, 'unixepoch', 'localtime') ELSE added_date END AS addedDate
        FROM word_favorite
        WHERE word LIKE :query OR definitions LIKE :query
        ORDER BY added_at DESC, word_id DESC
        """
    )
    suspend fun search(query: String): List<FavoriteWordRow>

    @Query("DELETE FROM word_favorite")
    suspend fun deleteAll()
}

data class FavoriteWordRow(
    val wordId: Long,
    val word: String,
    val definitions: String,
    val phonetic: String?,
    val addedDate: String
)