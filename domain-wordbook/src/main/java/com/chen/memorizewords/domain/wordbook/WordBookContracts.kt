package com.chen.memorizewords.domain.wordbook
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.word.WordRef
import kotlinx.coroutines.flow.Flow

data class WordBookRef(
    val id: Long,
    val name: String,
    val wordCount: Int
)

data class CurrentWordBook(
    val book: WordBookRef,
    val learnedCount: Int,
    val reviewCount: Int
)

interface WordBookRepository {
    fun observeCurrent(): Flow<CurrentWordBook?>
    suspend fun select(bookId: Long): Result<Unit>
    suspend fun pageWords(bookId: Long, pageIndex: Int, pageSize: Int): PageSlice<WordRef>
    suspend fun refreshFromServer(): Result<Unit>
}
