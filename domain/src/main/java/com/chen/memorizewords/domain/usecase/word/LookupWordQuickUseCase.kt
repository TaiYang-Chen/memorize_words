package com.chen.memorizewords.domain.usecase.word

import com.chen.memorizewords.domain.model.words.word.WordQuickLookupResult
import com.chen.memorizewords.domain.repository.word.WordRepository
import javax.inject.Inject

class LookupWordQuickUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(normalizedWord: String, rawWord: String): WordQuickLookupResult {
        return wordRepository.lookupWordQuick(normalizedWord, rawWord)
    }
}

