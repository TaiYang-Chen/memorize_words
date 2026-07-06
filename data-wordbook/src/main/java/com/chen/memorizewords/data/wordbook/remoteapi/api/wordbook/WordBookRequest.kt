package com.chen.memorizewords.data.wordbook.remoteapi.api.wordbook

import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordDto
import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.PageData
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.core.network.http.await
import com.chen.memorizewords.core.network.http.awaitNullable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordBookRequest @Inject constructor(
    private val wordBookApiService: WordBookApiService,
    private val requestExecutor: NetworkRequestExecutor
) {

    suspend fun getWordBooks(): NetworkResult<List<WordBookDto>> {
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

        return requestExecutor.executeAuthenticated {
            wordBookApiService.getWordBookWords(WordBookWordsRequest(bookId, page, count))
                .await<ApiResponse<PageData<WordDto>>, PageData<WordDto>>()
        }
    }

    suspend fun lookupWord(word: String, normalizedWord: String): NetworkResult<WordDto?> {
        if (word.isBlank() && normalizedWord.isBlank()) {
            return NetworkResult.Failure.GenericError("term or normalized is required")
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
}
