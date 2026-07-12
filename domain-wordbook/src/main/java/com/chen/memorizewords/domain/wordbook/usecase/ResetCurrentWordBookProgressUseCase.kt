package com.chen.memorizewords.domain.wordbook.usecase

import com.chen.memorizewords.domain.wordbook.repository.WordBookProgressResetRepository
import javax.inject.Inject

class ResetCurrentWordBookProgressUseCase @Inject constructor(
    private val repository: WordBookProgressResetRepository
) {
    suspend operator fun invoke(bookId: Long): Result<Unit> {
        require(bookId > 0L) { "bookId must be positive" }
        return repository.resetCurrentWordBookProgress(bookId)
    }
}
