package com.chen.memorizewords.domain.repository

import com.chen.memorizewords.domain.model.study.progress.word.WordLearningState

interface WordLearningRepository {
    suspend fun getLearningStatesByIds(wordBookId: Long, ids: List<Long>): Map<Long, WordLearningState>

    suspend fun getLearningStatesByBookId(bookId: Long): List<WordLearningState>

    suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long>

    suspend fun deleteLearningWordByBookId(bookId: Long)
}
