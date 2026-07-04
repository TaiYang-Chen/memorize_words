package com.chen.memorizewords.domain.wordbook.usecase

import com.chen.memorizewords.domain.wordbook.model.WordListSummary
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import javax.inject.Inject

class GetWordListSummaryUseCase @Inject constructor(
    private val wordBookRepository: WordBookRepository
) {
    suspend operator fun invoke(wordBookId: Long, now: Long = System.currentTimeMillis()): WordListSummary {
        return wordBookRepository.getWordListSummary(wordBookId, now)
    }
}
