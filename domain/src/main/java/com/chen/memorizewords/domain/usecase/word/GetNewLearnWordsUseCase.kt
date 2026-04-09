package com.chen.memorizewords.domain.usecase.word

import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.repository.WordBookRepository
import com.chen.memorizewords.domain.repository.WordOrderType
import javax.inject.Inject

class GetNewLearnWordsUseCase @Inject constructor(
    private val wordBookRepository: WordBookRepository
) {
    suspend operator fun invoke(
        bookId: Long,
        count: Int,
        orderType: WordOrderType = WordOrderType.RANDOM,
        excludeIds: Set<Long> = emptySet()
    ): List<Word> {
        val all = wordBookRepository.getAllUnlearnedWordsForBook(bookId)
        val filtered = if (excludeIds.isEmpty()) all else all.filter { it.id !in excludeIds }
        val sorted = when (orderType) {
            WordOrderType.RANDOM -> filtered.shuffled()
            WordOrderType.ALPHABETIC_ASC -> filtered.sortedBy { it.normalizedWord }
            WordOrderType.ALPHABETIC_DESC -> filtered.sortedByDescending { it.normalizedWord }
            WordOrderType.LENGTH_ASC -> filtered.sortedBy { it.word.length }
            WordOrderType.LENGTH_DESC -> filtered.sortedByDescending { it.word.length }
        }
        val data = sorted.take(count)
        return data
    }
}
