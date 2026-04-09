package com.chen.memorizewords.data.local.room.model.words.root.root

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.chen.memorizewords.data.local.room.model.words.root.rootexample.RootExampleEntity
import com.chen.memorizewords.data.local.room.model.words.root.rootmeaning.RootMeaningEntity
import com.chen.memorizewords.data.local.room.model.words.root.rootvariant.RootVariantEntity

/**
 * 词根模块的数据访问对象 (DAO)。
 * 提供对词根相关数据的增删改查方法。
 */
@Dao
interface WordRootDao {

    /**
     * 插入单个词根实体。
     * 如果词根已存在，则替换它。
     * @param root 要插入的 [WordRootEntity]。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(root: WordRootEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(root: List<WordRootEntity>)

    /**
     * 插入词根实体列表。
     * 如果词根已存在，则替换它们。
     * @param roots 要插入的 [WordRootEntity] 列表。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoots(roots: List<WordRootEntity>)

    /**
     * 插入单个词根含义实体。
     * @param meaning 要插入的 [RootMeaningEntity]。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootMeaning(meaning: RootMeaningEntity)

    /**
     * 插入词根含义实体列表。
     * @param meanings 要插入的 [RootMeaningEntity] 列表。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootMeanings(meanings: List<RootMeaningEntity>)

    /**
     * 插入单个词根变体实体。
     * @param variant 要插入的 [RootVariantEntity]。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootVariant(variant: RootVariantEntity)

    /**
     * 插入词根变体实体列表。
     * @param variants 要插入的 [RootVariantEntity] 列表。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootVariants(variants: List<RootVariantEntity>)

    /**
     * 插入单个词根示例实体。
     * @param example 要插入的 [RootExampleEntity]。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootExample(example: RootExampleEntity)

    /**
     * 插入词根示例实体列表。
     * @param examples 要插入的 [RootExampleEntity] 列表。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootExamples(examples: List<RootExampleEntity>)

    /**
     * 以事务方式根据 ID 查询单个词根及其详细信息。
     * @param id 要查询的词根的 ID。
     * @return 一个 [WordRootWithDetails] 对象，如果未找到则为 null。
     */
    @Transaction
    @Query("SELECT * FROM word_roots WHERE root_word = :rootWord")
    suspend fun getRootByRootWord(rootWord: String): List<WordRootEntity>

    @Transaction
    @Query("SELECT * FROM word_roots WHERE id = :id")
    suspend fun getWordRootById(id: Long): WordRootEntity?

    @Transaction
    @Query("SELECT * FROM word_roots WHERE id IN (:ids)")
    suspend fun getWordRootsByIds(ids: List<Long>): List<WordRootEntity>
}
