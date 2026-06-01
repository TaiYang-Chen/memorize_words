package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateJobState
import com.chen.memorizewords.domain.wordbook.repository.WordBookUpdateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveWordBookUpdateJobStateUseCase @Inject constructor(
    private val repository: WordBookUpdateRepository
) {
    operator fun invoke(bookId: Long): Flow<WordBookUpdateJobState> {
        return repository.observeUpdateJobState(bookId)
    }
}
