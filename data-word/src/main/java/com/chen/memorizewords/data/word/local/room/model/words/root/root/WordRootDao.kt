package com.chen.memorizewords.data.word.local.room.model.words.root.root

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.chen.memorizewords.data.word.local.room.model.words.root.rootexample.RootExampleEntity
import com.chen.memorizewords.data.word.local.room.model.words.root.rootmeaning.RootMeaningEntity
import com.chen.memorizewords.data.word.local.room.model.words.root.rootvariant.RootVariantEntity

/**
 * 璇嶆牴妯″潡鐨勬暟鎹闂锟?(DAO)锟?
 * 鎻愪緵瀵硅瘝鏍圭浉鍏虫暟鎹殑澧炲垹鏀规煡鏂规硶锟?
 */
@Dao
interface WordRootDao {

    /**
     * 鎻掑叆鍗曚釜璇嶆牴瀹炰綋锟?
     * 濡傛灉璇嶆牴宸插瓨鍦紝鍒欐浛鎹㈠畠锟?
     * @param root 瑕佹彃鍏ョ殑 [WordRootEntity]锟?
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(root: WordRootEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(root: List<WordRootEntity>)

    /**
     * 鎻掑叆璇嶆牴瀹炰綋鍒楄〃锟?
     * 濡傛灉璇嶆牴宸插瓨鍦紝鍒欐浛鎹㈠畠浠拷?
     * @param roots 瑕佹彃鍏ョ殑 [WordRootEntity] 鍒楄〃锟?
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoots(roots: List<WordRootEntity>)

    /**
     * 鎻掑叆鍗曚釜璇嶆牴鍚箟瀹炰綋锟?
     * @param meaning 瑕佹彃鍏ョ殑 [RootMeaningEntity]锟?
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootMeaning(meaning: RootMeaningEntity)

    /**
     * 鎻掑叆璇嶆牴鍚箟瀹炰綋鍒楄〃锟?
     * @param meanings 瑕佹彃鍏ョ殑 [RootMeaningEntity] 鍒楄〃锟?
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootMeanings(meanings: List<RootMeaningEntity>)

    /**
     * 鎻掑叆鍗曚釜璇嶆牴鍙樹綋瀹炰綋锟?
     * @param variant 瑕佹彃鍏ョ殑 [RootVariantEntity]锟?
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootVariant(variant: RootVariantEntity)

    /**
     * 鎻掑叆璇嶆牴鍙樹綋瀹炰綋鍒楄〃锟?
     * @param variants 瑕佹彃鍏ョ殑 [RootVariantEntity] 鍒楄〃锟?
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootVariants(variants: List<RootVariantEntity>)

    /**
     * 鎻掑叆鍗曚釜璇嶆牴绀轰緥瀹炰綋锟?
     * @param example 瑕佹彃鍏ョ殑 [RootExampleEntity]锟?
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootExample(example: RootExampleEntity)

    /**
     * 鎻掑叆璇嶆牴绀轰緥瀹炰綋鍒楄〃锟?
     * @param examples 瑕佹彃鍏ョ殑 [RootExampleEntity] 鍒楄〃锟?
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootExamples(examples: List<RootExampleEntity>)

    /**
     * 浠ヤ簨鍔℃柟寮忔牴锟?ID 鏌ヨ鍗曚釜璇嶆牴鍙婂叾璇︾粏淇℃伅锟?
     * @param id 瑕佹煡璇㈢殑璇嶆牴锟?ID锟?
     * @return 涓€锟?[WordRootWithDetails] 瀵硅薄锛屽鏋滄湭鎵惧埌鍒欎负 null锟?
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
