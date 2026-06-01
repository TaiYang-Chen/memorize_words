package com.chen.memorizewords.data.wordbook.repository

import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.toDomain
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.toEntity
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.StudySnapshotLocalStatePort
import com.chen.memorizewords.domain.study.repository.WordLearningStateStore
import javax.inject.Inject

class WordLearningStateMirror @Inject constructor(
    private val wordBookStateDao: WordLearningStateDao,
    private val studyStateStore: WordLearningStateStore,
    private val studySnapshotLocalStatePort: StudySnapshotLocalStatePort,
    private val transactionRunner: WordBookTransactionRunner
) {
    suspend fun getState(wordId: Long, bookId: Long): WordLearningState? {
        return studyStateStore.getState(wordId = wordId, bookId = bookId)
            ?: wordBookStateDao.getState(wordId = wordId, bookId = bookId)?.toDomain()
    }

    suspend fun upsertState(state: WordLearningState) {
        studyStateStore.upsertState(state)
        transactionRunner.runInTransaction {
            wordBookStateDao.upsert(state.toEntity())
        }
    }

    suspend fun overwriteLearningStatesForBookFromRemote(
        bookId: Long,
        states: List<WordLearningState>
    ) {
        studySnapshotLocalStatePort.overwriteLearningStatesForBookFromRemote(
            bookId = bookId,
            states = states
        )
        transactionRunner.runInTransaction {
            wordBookStateDao.deleteLearningWordByBookId(bookId)
            if (states.isNotEmpty()) {
                wordBookStateDao.upsertAll(states.map(WordLearningState::toEntity))
            }
        }
    }
}
