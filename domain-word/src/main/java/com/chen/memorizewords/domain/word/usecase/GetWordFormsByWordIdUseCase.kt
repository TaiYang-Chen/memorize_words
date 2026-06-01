package com.chen.memorizewords.domain.word.usecase
import com.chen.memorizewords.domain.word.model.word.WordForm
import com.chen.memorizewords.domain.word.repository.WordRepository
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