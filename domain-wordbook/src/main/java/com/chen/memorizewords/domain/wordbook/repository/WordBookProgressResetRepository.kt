package com.chen.memorizewords.domain.wordbook.repository

interface WordBookProgressResetRepository {
    suspend fun resetCurrentWordBookProgress(bookId: Long): Result<Unit>
}
