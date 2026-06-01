package com.chen.memorizewords.domain.word.usecase
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.repository.WordRepository
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
