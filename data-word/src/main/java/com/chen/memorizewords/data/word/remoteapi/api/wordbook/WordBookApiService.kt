package com.chen.memorizewords.data.word.remoteapi.api.wordbook

import com.chen.memorizewords.data.word.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.data.word.remoteapi.dto.wordbook.WordDto
import com.chen.memorizewords.core.network.http.ApiResponse
import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@JsonClass(generateAdapter = false)
data class WordLookupRequest(
    val term: String,
    val normalized: String
)

interface WordBookApiService {
    companion object {
        const val PATH_WORD_BOOKS = "wordbook/list"
        const val PATH_WORD_LOOKUP = "wordbook/lookup"
    }

    @GET(PATH_WORD_BOOKS)
    fun getWordBooks(): Call<ApiResponse<List<WordBookDto>>>

    @POST(PATH_WORD_LOOKUP)
    fun lookupWord(@Body request: WordLookupRequest): Call<ApiResponse<WordDto?>>
}
