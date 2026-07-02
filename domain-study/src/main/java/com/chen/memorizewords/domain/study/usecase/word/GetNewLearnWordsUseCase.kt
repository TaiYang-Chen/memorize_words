package com.chen.memorizewords.domain.study.usecase.word
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import javax.inject.Inject

class GetNewLearnWordsUseCase @Inject constructor(
    private val wordBookRepository: WordBookRepository
) {
    suspend operator fun invoke(
        bookId: Long,
        count: Int,
        orderType: WordOrderType = WordOrderType.RANDOM,
        excludeIds: Set<Long> = emptySet()
    ): List<Long> {
        return wordBookRepository.getUnlearnedWordIdsForBook(
            bookId = bookId,
            count = count,
            orderType = orderType,
            excludeIds = excludeIds
        )
    }
}
