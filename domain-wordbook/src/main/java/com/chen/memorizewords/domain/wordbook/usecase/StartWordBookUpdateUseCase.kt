package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateExecutionMode
import com.chen.memorizewords.domain.wordbook.repository.WordBookUpdateRepository
import javax.inject.Inject

class StartWordBookUpdateUseCase @Inject constructor(
    private val repository: WordBookUpdateRepository
) {
    suspend operator fun invoke(bookId: Long, targetVersion: Long) {
        repository.enqueueUpdate(bookId, targetVersion, WordBookUpdateExecutionMode.MANUAL)
    }
}
