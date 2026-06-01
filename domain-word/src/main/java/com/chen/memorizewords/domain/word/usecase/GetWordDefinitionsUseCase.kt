package com.chen.memorizewords.domain.word.usecase
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.repository.WordRepository
import javax.inject.Inject

class GetWordDefinitionsUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(wordId: Long): List<WordDefinitions> {
        return wordRepository.getWordDefinitions(wordId)
    }
}