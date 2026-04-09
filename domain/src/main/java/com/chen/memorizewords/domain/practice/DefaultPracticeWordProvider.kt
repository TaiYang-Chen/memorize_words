package com.chen.memorizewords.domain.practice

import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.repository.WordBookRepository
import com.chen.memorizewords.domain.repository.WordOrderType
import com.chen.memorizewords.domain.repository.practice.PracticeSettingsRepository
import com.chen.memorizewords.domain.repository.word.WordRepository
import com.chen.memorizewords.domain.usecase.word.GetReviewWordsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultPracticeWordProvider @Inject constructor(
    private val practiceSettingsRepository: PracticeSettingsRepository,
    private val wordBookRepository: WordBookRepository,
    private val wordRepository: WordRepository,
    private val getReviewWords: GetReviewWordsUseCase
) : PracticeWordProvider {

    override suspend fun loadWords(
        selectedIds: LongArray?,
        randomCount: Int,
        defaultLimit: Int
    ): List<Word> {
        return withContext(Dispatchers.IO) {
            val ids = selectedIds?.takeIf { it.isNotEmpty() }
            if (ids != null) {
                val words = mutableListOf<Word>()
                for (id in ids) {
                    wordRepository.getWordById(id)?.let { words.add(it) }
                }
                return@withContext words
            }

            val bookId = resolveBookId() ?: return@withContext emptyList()
            val count = if (randomCount > 0) randomCount else defaultLimit
            getReviewWords(
                bookId = bookId,
                count = count,
                orderType = WordOrderType.RANDOM
            ).shuffled()
        }
    }

    override suspend fun getPracticeAvailability(): PracticeAvailability {
        return withContext(Dispatchers.IO) {
            val bookId = resolveBookId() ?: return@withContext PracticeAvailability.NO_BOOK
            val words = getReviewWords(
                bookId = bookId,
                count = 1,
                orderType = WordOrderType.RANDOM
            )
            if (words.isEmpty()) PracticeAvailability.NO_WORDS else PracticeAvailability.AVAILABLE
        }
    }

    override suspend fun resolveBookId(): Long? {
        val settingsBookId = practiceSettingsRepository.getSettings().selectedBookId
        if (settingsBookId > 0L) return settingsBookId
        return withContext(Dispatchers.IO) { wordBookRepository.getCurrentWordBook()?.id }
    }

    override suspend fun loadReviewWordsForPicker(): List<Word> {
        return withContext(Dispatchers.IO) {
            val bookId = resolveBookId() ?: return@withContext emptyList()
            getReviewWords(
                bookId = bookId,
                count = Int.MAX_VALUE,
                orderType = WordOrderType.ALPHABETIC_ASC
            )
        }
    }
}
