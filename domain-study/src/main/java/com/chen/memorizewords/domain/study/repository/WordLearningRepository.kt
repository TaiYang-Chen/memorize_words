package com.chen.memorizewords.domain.study.repository
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState

interface WordLearningRepository {
    suspend fun getLearningStatesByIds(wordBookId: Long, ids: List<Long>): Map<Long, WordLearningState>

    suspend fun getLearningStatesByBookId(bookId: Long): List<WordLearningState>

    suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long>

    suspend fun deleteLearningWordByBookId(bookId: Long)
}
