package com.chen.memorizewords.domain.study.repository.learning

interface BookLearningWriteCoordinator {
    suspend fun <T> withBookWrite(bookId: Long, block: suspend () -> T): T
}
