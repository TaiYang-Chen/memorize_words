package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.domain.wordbook.repository.WordBookUpdateRepository
import javax.inject.Inject

class IgnoreWordBookUpdateUseCase @Inject constructor(
    private val repository: WordBookUpdateRepository
) {
    suspend operator fun invoke(bookId: Long, targetVersion: Long): Result<Unit> {
        return repository.ignoreUpdate(bookId, targetVersion)
    }
}
