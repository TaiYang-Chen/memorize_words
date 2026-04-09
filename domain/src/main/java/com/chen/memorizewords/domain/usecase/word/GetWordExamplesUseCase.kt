package com.chen.memorizewords.domain.usecase.word

import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.domain.repository.word.WordRepository
import javax.inject.Inject

class GetWordExamplesUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(wordId: Long): List<WordExample> {
        return wordRepository.getWordExamples(wordId)
    }
}