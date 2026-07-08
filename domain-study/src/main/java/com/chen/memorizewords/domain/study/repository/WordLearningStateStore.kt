package com.chen.memorizewords.domain.study.repository

import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState

interface WordLearningStateStore {
    suspend fun getState(wordId: Long, bookId: Long): WordLearningState?
}
