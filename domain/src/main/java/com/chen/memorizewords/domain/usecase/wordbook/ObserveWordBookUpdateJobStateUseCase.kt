package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateJobState
import com.chen.memorizewords.domain.repository.WordBookUpdateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveWordBookUpdateJobStateUseCase @Inject constructor(
    private val repository: WordBookUpdateRepository
) {
    operator fun invoke(bookId: Long): Flow<WordBookUpdateJobState> {
        return repository.observeUpdateJobState(bookId)
    }
}
