package com.chen.memorizewords.domain.usecase.word

import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.repository.WordLearningRepository
import com.chen.memorizewords.domain.repository.WordOrderType
import com.chen.memorizewords.domain.repository.word.WordRepository
import javax.inject.Inject

class GetReviewWordsUseCase @Inject constructor(
    private val wordLearningRepository: WordLearningRepository,
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(
        bookId: Long,
        count: Int,
        orderType: WordOrderType = WordOrderType.RANDOM,
        excludeIds: Set<Long> = emptySet()
    ): List<Word> {
        if (count <= 0) return emptyList()
        val learnedIds = wordLearningRepository.getLearnedWordIdsByBook(bookId)
            .filterNot { it in excludeIds }
        if (learnedIds.isEmpty()) return emptyList()

        if (orderType == WordOrderType.RANDOM) {
            val selectedIds = selectRandomReviewWordIds(learnedIds, count)
            if (selectedIds.isEmpty()) return emptyList()
            val words = wordRepository.getWordsByIds(selectedIds)
            val wordMap = words.associateBy { it.id }
            return selectedIds.mapNotNull { wordMap[it] }
        }

        val words = wordRepository.getWordsByIds(learnedIds)
        val wordMap = words.associateBy { it.id }
        val orderedWords = learnedIds.mapNotNull { wordMap[it] }
        val sorted = when (orderType) {
            WordOrderType.RANDOM -> orderedWords
            WordOrderType.ALPHABETIC_ASC -> orderedWords.sortedBy { it.normalizedWord }
            WordOrderType.ALPHABETIC_DESC -> orderedWords.sortedByDescending { it.normalizedWord }
            WordOrderType.LENGTH_ASC -> orderedWords.sortedBy { it.word.length }
            WordOrderType.LENGTH_DESC -> orderedWords.sortedByDescending { it.word.length }
        }
        return sorted.take(count)
    }
}

internal fun selectRandomReviewWordIds(
    learnedIds: List<Long>,
    count: Int,
    shuffle: (List<Long>) -> List<Long> = { ids -> ids.shuffled() }
): List<Long> {
    if (count <= 0 || learnedIds.isEmpty()) return emptyList()
    return shuffle(learnedIds).take(count)
}
