package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.repository.WordBookUpdateRepository
import javax.inject.Inject

class IgnoreWordBookUpdateUseCase @Inject constructor(
    private val repository: WordBookUpdateRepository
) {
    suspend operator fun invoke(bookId: Long, targetVersion: Long): Result<Unit> {
        return repository.ignoreUpdate(bookId, targetVersion)
    }
}
