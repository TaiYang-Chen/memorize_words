package com.chen.memorizewords.data.wordbook.repository.learning

import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionDao
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.wordbook.repository.WordBookTransactionRunner
import com.chen.memorizewords.domain.study.repository.learning.BookLearningWriteCoordinator
import com.chen.memorizewords.domain.wordbook.repository.WordBookProgressResetRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WordBookProgressResetRepositoryImpl @Inject constructor(
    private val coordinator: BookLearningWriteCoordinator,
    private val transactionRunner: WordBookTransactionRunner,
    private val currentWordBookSelectionDao: CurrentWordBookSelectionDao,
    private val wordLearningStateDao: WordLearningStateDao,
    private val wordBookProgressDao: WordBookProgressDao,
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource
) : WordBookProgressResetRepository {

    override suspend fun resetCurrentWordBookProgress(bookId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                coordinator.withBookWrite(bookId) {
                    check(currentWordBookSelectionDao.getById()?.bookId == bookId) {
                        "word book is no longer current"
                    }
                    remoteUserSyncDataSource.resetWordBookProgress(bookId).getOrThrow()
                    transactionRunner.runInTransaction {
                        wordLearningStateDao.deleteLearningWordByBookId(bookId)
                        wordBookProgressDao.deleteByBookId(bookId)
                    }
                }
            }
        }
}
