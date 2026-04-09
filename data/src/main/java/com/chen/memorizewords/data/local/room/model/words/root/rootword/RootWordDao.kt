package com.chen.memorizewords.data.local.room.model.words.root.rootword

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * `RootWordEntity` 的数据访问对象 (DAO)。
 * 提供对 `word_root_relation` 表的数据库操作。
 */
@Dao
interface RootWordDao {

    /**
     * 插入一个单词与词根的关联。
     * 如果已存在，则替换。
     *
     * @param rootWord 要插入的关联实体。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rootWord: RootWordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rootWords: List<RootWordEntity>)

    /**
     * 插入一组单词与词根的关联。
     *
     * @param rootWords 要插入的关联实体列表。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rootWords: List<RootWordEntity>)

    /**
     * 根据单词 ID 获取其所有相关的词根，并按顺序排列。
     *
     * @param wordId 单词的 ID。
     * @return 一个 Flow，它发出与该单词关联的 [RootWordEntity] 列表。
     */
    @Query("SELECT * FROM word_root_relation WHERE wordId = :wordId ORDER BY sequence ASC")
    fun getRootsForWordId(wordId: Long): List<RootWordEntity>

    /**
     * 根据词根 ID 获取所有相关的单词。
     *
     * @param rootId 词根的 ID。
     * @return 一个 Flow，它发出与该词根关联的 [RootWordEntity] 列表。
     */
    @Query("SELECT * FROM word_root_relation WHERE rootId = :rootId")
    fun getWordsForRoot(rootId: Long): Flow<List<RootWordEntity>>

    @Query("DELETE FROM word_root_relation WHERE wordId = :wordId")
    suspend fun deleteByWordId(wordId: Long)

    /**
     * 删除一个指定的单词-词根关联。
     *
     * @param rootWord 要删除的关联实体。
     */
    @Query("DELETE FROM word_root_relation WHERE wordId = :wordId AND sequence = :sequence")
    suspend fun delete(wordId: Long, sequence: Int)

    /**
     * 清空所有单词-词根关联数据。
     */
    @Query("DELETE FROM word_root_relation")
    suspend fun clearAll()
}
