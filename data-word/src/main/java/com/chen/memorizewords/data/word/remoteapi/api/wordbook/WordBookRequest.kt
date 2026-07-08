package com.chen.memorizewords.data.word.remoteapi.api.wordbook

import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.data.word.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.data.word.remoteapi.dto.wordbook.WordDto
import com.chen.memorizewords.core.network.http.ApiResponse
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

    suspend fun lookupWord(word: String, normalizedWord: String): NetworkResult<WordDto?> {
        if (word.isBlank() && normalizedWord.isBlank()) {
            return NetworkResult.Failure.GenericError("term or normalized is required")
        }
        return requestExecutor.executeAuthenticated {
            wordBookApiService.lookupWord(WordLookupRequest(word, normalizedWord))
                .awaitNullable<ApiResponse<WordDto?>, WordDto>()
        }
    }
}
