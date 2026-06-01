package com.chen.memorizewords.domain.word.usecase
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.repository.WordRepository
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