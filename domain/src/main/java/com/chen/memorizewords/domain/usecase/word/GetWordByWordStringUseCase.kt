package com.chen.memorizewords.domain.usecase.word

import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.repository.word.WordRepository
import javax.inject.Inject

class GetWordByWordStringUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(
        word: String
    ): Word? {
        return wordRepository.getWordByWordString(word)
    }
}