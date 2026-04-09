package com.chen.memorizewords.data.local.room.model.study.favorites

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
            f.word_id AS wordId,
            COALESCE(w.word, '') AS word,
            COALESCE(w.phonetic_us, w.phonetic_uk) AS phonetic,
            f.added_date AS addedDate,
            COALESCE((
                SELECT group_concat(part_of_speech || ' ' || meaning_chinese, ' ')
                FROM word_definitions d
                WHERE d.word_id = f.word_id
            ), '') AS definitions
        FROM word_favorite f
        LEFT JOIN words w ON w.id = f.word_id
        ORDER BY f.added_date DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getPagedRows(limit: Int, offset: Int): List<FavoriteWordRow>

    @Query(
        """
        SELECT
            f.word_id AS wordId,
            COALESCE(w.word, '') AS word,
            COALESCE(w.phonetic_us, w.phonetic_uk) AS phonetic,
            f.added_date AS addedDate,
            COALESCE((
                SELECT group_concat(part_of_speech || ' ' || meaning_chinese, ' ')
                FROM word_definitions d
                WHERE d.word_id = f.word_id
            ), '') AS definitions
        FROM word_favorite f
        LEFT JOIN words w ON w.id = f.word_id
        ORDER BY f.added_date DESC
        """
    )
    suspend fun getAllRows(): List<FavoriteWordRow>

    @Query(
        """
        SELECT
            f.word_id AS wordId,
            COALESCE(w.word, '') AS word,
            COALESCE(w.phonetic_us, w.phonetic_uk) AS phonetic,
            f.added_date AS addedDate,
            COALESCE((
                SELECT group_concat(part_of_speech || ' ' || meaning_chinese, ' ')
                FROM word_definitions d
                WHERE d.word_id = f.word_id
            ), '') AS definitions
        FROM word_favorite f
        LEFT JOIN words w ON w.id = f.word_id
        ORDER BY f.added_date DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentRows(limit: Int): List<FavoriteWordRow>

    @Query(
        """
        SELECT
            f.word_id AS wordId,
            COALESCE(w.word, '') AS word,
            COALESCE(w.phonetic_us, w.phonetic_uk) AS phonetic,
            f.added_date AS addedDate,
            COALESCE(group_concat(d.part_of_speech || ' ' || d.meaning_chinese, ' '), '') AS definitions
        FROM word_favorite f
        LEFT JOIN words w ON w.id = f.word_id
        LEFT JOIN word_definitions d ON d.word_id = f.word_id
        WHERE w.word LIKE :query OR d.meaning_chinese LIKE :query
        GROUP BY f.word_id
        ORDER BY f.added_date DESC
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
