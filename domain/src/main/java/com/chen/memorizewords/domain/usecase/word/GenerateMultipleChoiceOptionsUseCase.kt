package com.chen.memorizewords.domain.usecase.word

import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.repository.word.WordRepository
import javax.inject.Inject

class GenerateMultipleChoiceOptionsUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(
        wordId: Long
    ): List<WordDefinitions> {
        val correct = wordRepository.getRandomDefinition(wordId)
        val distractors = wordRepository.getRandomDefinitionsByPos(wordId, 3)
        return (listOf(correct) + distractors)
            .distinctBy { it.id }
            .shuffled()
    }
}
