package com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootword

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * `RootWordEntity` 鐨勬暟鎹闂锟?(DAO)锟?
 * 鎻愪緵锟?`word_root_relation` 琛ㄧ殑鏁版嵁搴撴搷浣滐拷?
 */
@Dao
interface RootWordDao {

    /**
     * 鎻掑叆涓€涓崟璇嶄笌璇嶆牴鐨勫叧鑱旓拷?
     * 濡傛灉宸插瓨鍦紝鍒欐浛鎹拷?
     *
     * @param rootWord 瑕佹彃鍏ョ殑鍏宠仈瀹炰綋锟?
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rootWord: RootWordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rootWords: List<RootWordEntity>)

    /**
     * 鎻掑叆涓€缁勫崟璇嶄笌璇嶆牴鐨勫叧鑱旓拷?
     *
     * @param rootWords 瑕佹彃鍏ョ殑鍏宠仈瀹炰綋鍒楄〃锟?
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rootWords: List<RootWordEntity>)

    /**
     * 鏍规嵁鍗曡瘝 ID 鑾峰彇鍏舵墍鏈夌浉鍏崇殑璇嶆牴锛屽苟鎸夐『搴忔帓鍒楋拷?
     *
     * @param wordId 鍗曡瘝锟?ID锟?
     * @return 涓€锟?Flow锛屽畠鍙戝嚭涓庤鍗曡瘝鍏宠仈锟?[RootWordEntity] 鍒楄〃锟?
     */
    @Query("SELECT * FROM word_root_relation WHERE word_id = :wordId ORDER BY sequence ASC")
    fun getRootsForWordId(wordId: Long): List<RootWordEntity>

    /**
     * 鏍规嵁璇嶆牴 ID 鑾峰彇鎵€鏈夌浉鍏崇殑鍗曡瘝锟?
     *
     * @param rootId 璇嶆牴锟?ID锟?
     * @return 涓€锟?Flow锛屽畠鍙戝嚭涓庤璇嶆牴鍏宠仈锟?[RootWordEntity] 鍒楄〃锟?
     */
    @Query("SELECT * FROM word_root_relation WHERE root_id = :rootId")
    fun getWordsForRoot(rootId: Long): Flow<List<RootWordEntity>>

    @Query("DELETE FROM word_root_relation WHERE word_id = :wordId")
    suspend fun deleteByWordId(wordId: Long)

    /**
     * 鍒犻櫎涓€涓寚瀹氱殑鍗曡瘝-璇嶆牴鍏宠仈锟?
     *
     * @param rootWord 瑕佸垹闄ょ殑鍏宠仈瀹炰綋锟?
     */
    @Query("DELETE FROM word_root_relation WHERE word_id = :wordId AND sequence = :sequence")
    suspend fun delete(wordId: Long, sequence: Int)

    /**
     * 娓呯┖鎵€鏈夊崟锟?璇嶆牴鍏宠仈鏁版嵁锟?
     */
    @Query("DELETE FROM word_root_relation")
    suspend fun clearAll()
}
