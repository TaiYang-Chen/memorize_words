package com.chen.memorizewords.domain.wordbook.usecase

import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCurrentWordBookSelectionIdUseCase @Inject constructor(
    private val repository: WordBookRepository
) {
    operator fun invoke(): Flow<Long?> = repository.observeCurrentWordBookSelectionId()
}
