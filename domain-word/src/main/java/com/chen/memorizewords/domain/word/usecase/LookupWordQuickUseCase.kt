package com.chen.memorizewords.domain.word.usecase
import com.chen.memorizewords.domain.word.model.word.WordQuickLookupResult
import com.chen.memorizewords.domain.word.repository.WordRepository
import javax.inject.Inject

class LookupWordQuickUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(normalizedWord: String, rawWord: String): WordQuickLookupResult {
        return wordRepository.lookupWordQuick(normalizedWord, rawWord)
    }
}

