package com.chen.memorizewords.data.wordbook.repository

import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.toEntity
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import javax.inject.Inject

class WordLearningStateSnapshotStore @Inject constructor(
    private val wordBookStateDao: WordLearningStateDao,
    private val transactionRunner: WordBookTransactionRunner
) {
    suspend fun overwriteLearningStatesForBookFromRemote(
        bookId: Long,
        states: List<WordLearningState>
    ) {
        transactionRunner.runInTransaction {
            wordBookStateDao.deleteLearningWordByBookId(bookId)
            if (states.isNotEmpty()) {
                wordBookStateDao.upsertAll(states.map(WordLearningState::toEntity))
            }
        }
    }
}
