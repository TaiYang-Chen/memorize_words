package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateExecutionMode
import com.chen.memorizewords.domain.repository.WordBookUpdateRepository
import javax.inject.Inject

class StartWordBookUpdateUseCase @Inject constructor(
    private val repository: WordBookUpdateRepository
) {
    suspend operator fun invoke(bookId: Long, targetVersion: Long) {
        repository.enqueueUpdate(bookId, targetVersion, WordBookUpdateExecutionMode.MANUAL)
    }
}
