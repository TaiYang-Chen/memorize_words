package com.chen.memorizewords.domain.usecase.word

import com.chen.memorizewords.domain.model.words.word.WordForm
import com.chen.memorizewords.domain.repository.word.WordRepository
import javax.inject.Inject

class GetWordFormsByWordIdUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(
        wordId: Long
    ): List<WordForm> {
        return wordRepository.getWordForms(wordId)
    }
}