package com.chen.memorizewords.domain.wordbook.usecase

import com.chen.memorizewords.domain.wordbook.model.WordBookContentState
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveWordBookContentStateUseCase @Inject constructor(
    private val repository: WordBookRepository
) {
    operator fun invoke(bookId: Long): Flow<WordBookContentState?> =
        repository.observeWordBookContentState(bookId)
}
