package com.chen.memorizewords.network.api.wordbook

import com.chen.memorizewords.network.api.NetworkRequestExecutor
import com.chen.memorizewords.network.dto.wordbook.WordBookDto
import com.chen.memorizewords.network.dto.wordbook.WordDefinitionDto
import com.chen.memorizewords.network.dto.wordbook.WordDto
import com.chen.memorizewords.network.dto.wordbook.WordExampleDto
import com.chen.memorizewords.network.dto.wordbook.WordFormDto
import com.chen.memorizewords.network.model.ApiResponse
import com.chen.memorizewords.network.model.PageData
import com.chen.memorizewords.network.util.NetworkResult
import com.chen.memorizewords.network.util.await
import com.chen.memorizewords.network.util.awaitNullable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordBookRequest @Inject constructor(
    private val wordBookApiService: WordBookApiService,
    private val requestExecutor: NetworkRequestExecutor
) {

    suspend fun getWordBooks(): NetworkResult<List<WordBookDto>> {
        if (USE_FAKE_DATA) {
            return NetworkResult.Success(fakeWordBooks())
        }
        return requestExecutor.executeAuthenticated {
            wordBookApiService.getWordBooks()
                .await<ApiResponse<List<WordBookDto>>, List<WordBookDto>>()
        }
    }

    suspend fun getWordBookWords(
        bookId: Long,
        page: Int,
        count: Int
    ): NetworkResult<PageData<WordDto>> {
        val validationError = validateWordBookWordsParams(bookId, page, count)
        if (validationError != null) {
            return NetworkResult.Failure.GenericError(validationError)
        }

        if (USE_FAKE_DATA) {
            val all = fakeWordsForBook(bookId)
            val fromIndex = (page * count).coerceAtLeast(0)
            val toIndex = (fromIndex + count).coerceAtMost(all.size)
            val items = if (fromIndex >= all.size) emptyList() else all.subList(fromIndex, toIndex)
            return NetworkResult.Success(PageData(items, page, count, all.size.toLong()))
        }

        return requestExecutor.executeAuthenticated {
            wordBookApiService.getWordBookWords(WordBookWordsRequest(bookId, page, count))
                .await<ApiResponse<PageData<WordDto>>, PageData<WordDto>>()
        }
    }

    suspend fun lookupWord(word: String, normalizedWord: String): NetworkResult<WordDto?> {
        if (word.isBlank() || normalizedWord.isBlank()) {
            return NetworkResult.Failure.GenericError("word and normalizedWord cannot be blank")
        }
        return requestExecutor.executeAuthenticated {
            wordBookApiService.lookupWord(WordLookupRequest(word, normalizedWord))
                .awaitNullable<ApiResponse<WordDto?>, WordDto>()
        }
    }

    private fun validateWordBookWordsParams(bookId: Long, page: Int, count: Int): String? {
        return when {
            bookId <= 0L -> "bookId must be > 0"
            page < 0 -> "page must be >= 0"
            count <= 0 -> "count must be > 0"
            else -> null
        }
    }

    private companion object {
        const val USE_FAKE_DATA = false
        private const val FAKE_BOOK_COUNT = 20
        private const val FAKE_WORDS_PER_BOOK = 10

        private val categories = listOf("college", "high_school", "middle_school", "cet4", "cet6")

        private fun fakeWordBooks(): List<WordBookDto> {
            return (1..FAKE_BOOK_COUNT).map { index ->
                WordBookDto(
                    id = index.toLong(),
                    title = "wordbook$index",
                    category = categories[(index - 1) % categories.size],
                    imgUrl = "",
                    description = "mock wordbook $index",
                    totalWords = FAKE_WORDS_PER_BOOK,
                    learnedWords = 0,
                    updatedAt = 0L,
                    isNew = index % 3 == 0,
                    isHot = index % 4 == 0,
                    isSelected = false,
                    isPublic = true,
                    createdByUserId = null
                )
            }
        }

        private fun fakeWordsForBook(bookId: Long): List<WordDto> {
            if (bookId !in 1L..FAKE_BOOK_COUNT.toLong()) return emptyList()
            val base = (bookId - 1) * FAKE_WORDS_PER_BOOK
            return (1..FAKE_WORDS_PER_BOOK).map { idx ->
                val wordId = base + idx
                val wordText = "word$wordId"
                val definition = WordDefinitionDto(
                    id = wordId,
                    wordId = wordId,
                    partOfSpeech = "n",
                    definition = "mock definition $wordId"
                )
                val example = WordExampleDto(
                    id = wordId,
                    wordId = wordId,
                    definitionId = wordId,
                    englishSentence = "This is $wordText.",
                    chineseTranslation = "This is $wordText.",
                    difficultyLevel = 3
                )
                val form = WordFormDto(
                    id = wordId,
                    wordId = wordId,
                    formWordId = null,
                    formType = "PLURAL",
                    formText = "${wordText}s"
                )

                WordDto(
                    id = wordId,
                    word = wordText,
                    normalizedWord = wordText,
                    phoneticUS = "/word$wordId/",
                    phoneticUK = "/word$wordId/",
                    hasIrregularForms = false,
                    memoryTip = "tip $wordId",
                    rootMemoryTip = null,
                    mnemonicImageUrl = null,
                    memoryAssociations = listOf("assoc$wordId"),
                    wordFamily = "family$wordId",
                    synonyms = listOf("syn$wordId"),
                    antonyms = listOf("ant$wordId"),
                    tags = listOf("mock"),
                    notes = "note $wordId",
                    definitionDtos = arrayListOf(definition),
                    exampleDtos = arrayListOf(example),
                    wordFormDtos = arrayListOf(form),
                    rootWords = arrayListOf()
                )
            }
        }
    }
}
