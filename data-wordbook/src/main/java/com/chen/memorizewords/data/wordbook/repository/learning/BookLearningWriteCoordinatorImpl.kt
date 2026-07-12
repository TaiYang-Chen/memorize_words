package com.chen.memorizewords.data.wordbook.repository.learning

import com.chen.memorizewords.domain.study.repository.learning.BookLearningWriteCoordinator
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class BookLearningWriteCoordinatorImpl @Inject constructor() : BookLearningWriteCoordinator {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    override suspend fun <T> withBookWrite(bookId: Long, block: suspend () -> T): T {
        require(bookId > 0L) { "bookId must be positive" }
        return locks.getOrPut(bookId) { Mutex() }.withLock { block() }
    }
}
