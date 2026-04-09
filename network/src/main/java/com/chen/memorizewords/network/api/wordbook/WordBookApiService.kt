package com.chen.memorizewords.network.api.wordbook

import com.chen.memorizewords.network.dto.wordbook.WordBookDto
import com.chen.memorizewords.network.dto.wordbook.WordDto
import com.chen.memorizewords.network.model.ApiResponse
import com.chen.memorizewords.network.model.PageData
import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@JsonClass(generateAdapter = false)
data class WordBookWordsRequest(
    val bookId: Long,
    val page: Int,
    val count: Int
)

@JsonClass(generateAdapter = false)
data class WordLookupRequest(
    val word: String,
    val normalizedWord: String
)

interface WordBookApiService {
    companion object {
        const val PATH_WORD_BOOKS = "wordbook/list"
        const val PATH_WORD_BOOK_WORDS = "wordbook/words"
        const val PATH_WORD_LOOKUP = "wordbook/lookup"
    }

    @GET(PATH_WORD_BOOKS)
    fun getWordBooks(): Call<ApiResponse<List<WordBookDto>>>

    @POST(PATH_WORD_BOOK_WORDS)
    fun getWordBookWords(@Body request: WordBookWordsRequest): Call<ApiResponse<PageData<WordDto>>>

    @POST(PATH_WORD_LOOKUP)
    fun lookupWord(@Body request: WordLookupRequest): Call<ApiResponse<WordDto?>>
}
