package com.chen.memorizewords.domain.repository

import com.chen.memorizewords.domain.model.common.PageSlice
import com.chen.memorizewords.domain.model.wordbook.WordBook
import com.chen.memorizewords.domain.model.wordbook.WordBookInfo
import com.chen.memorizewords.domain.model.wordbook.WordListQuery
import com.chen.memorizewords.domain.model.words.WordListRow
import com.chen.memorizewords.domain.model.words.word.Word
import kotlinx.coroutines.flow.Flow

interface WordBookRepository {
    fun getMyWordBooksMinimalFlow(): Flow<List<WordBookInfo>>
    fun getCurrentWordBookMinimalFlow(): Flow<WordBookInfo?>
    suspend fun setCurrentWordBook(bookId: Long)
    suspend fun getCurrentWordBook(): WordBook?

    suspend fun getBookNameById(bookId: Long): String?
    suspend fun getWordRowsPage(query: WordListQuery): PageSlice<WordListRow>

    /**
     * 按页返回某个词书对应的 wordId 列表（分页友好）。
     *
     * @param wordBookId 词书 id
     * @param pageIndex  从 0 开始的页码
     * @param pageSize   每页大小
     * @return 单页的 wordId 列表（按顺序）
     */
    suspend fun getWordIdsPage(wordBookId: Long, pageIndex: Int, pageSize: Int): List<Long>

    suspend fun getAllUnlearnedWordsForBook(bookId: Long): List<Word>


    suspend fun updateBookStudyDay(bookId: Long, today: String)
    suspend fun recordAnswerResult(bookId: Long, isCorrect: Boolean, today: String)
}

/**
 * 单词排序方式枚举
 */
enum class WordOrderType {
    /**
     * 随机排序
     * 适合日常学习和测试
     */
    RANDOM,

    /**
     * 按字母顺序升序排列 (A-Z)
     * 适合按字典顺序学习
     */
    ALPHABETIC_ASC,

    /**
     * 按字母顺序降序排列 (Z-A)
     */
    ALPHABETIC_DESC,

    /**
     * 按单词长度升序排列
     * 适合从简单单词开始学习
     */
    LENGTH_ASC,

    /**
     * 按单词长度降序排列
     * 适合挑战难度较大的单词
     */
    LENGTH_DESC
}
