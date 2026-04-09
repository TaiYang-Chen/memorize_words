package com.chen.memorizewords.network.api.practice

import com.chen.memorizewords.network.model.ApiResponse
import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@JsonClass(generateAdapter = false)
data class ExamItemStateDto(
    val examItemId: Long,
    val favorite: Boolean = false,
    val wrongBook: Boolean = false,
    val attemptCount: Int = 0,
    val correctCount: Int = 0,
    val lastResult: String? = null,
    val lastAnsweredAt: Long? = null
)

@JsonClass(generateAdapter = false)
data class WordExamItemDto(
    val id: Long,
    val wordId: Long,
    val questionType: String,
    val examCategory: String,
    val paperName: String,
    val difficultyLevel: Int,
    val sortOrder: Int,
    val groupKey: String? = null,
    val contentText: String,
    val contextText: String? = null,
    val options: List<String> = emptyList(),
    val answers: List<String> = emptyList(),
    val leftItems: List<String> = emptyList(),
    val rightItems: List<String> = emptyList(),
    val answerIndexes: List<Int> = emptyList(),
    val analysisText: String? = null,
    val state: ExamItemStateDto? = null
)

@JsonClass(generateAdapter = false)
data class ExamPracticeWordResponseDto(
    val wordId: Long,
    val word: String,
    val examItemDtos: List<WordExamItemDto> = emptyList(),
    val totalCount: Int = 0,
    val favoriteCount: Int = 0,
    val wrongCount: Int = 0,
    val objectiveCount: Int = 0
)

@JsonClass(generateAdapter = false)
data class ExamItemFavoriteRequest(
    val favorite: Boolean
)

@JsonClass(generateAdapter = false)
data class ExamPracticeSessionItemAnswerDto(
    val itemId: Long,
    val answers: List<String> = emptyList(),
    val answerIndexes: List<Int> = emptyList(),
    val viewedAnswer: Boolean = false,
    val submitCount: Int = 0
)

@JsonClass(generateAdapter = false)
data class ExamPracticeSessionSubmitRequest(
    val wordId: Long,
    val sessionId: Long? = null,
    val durationMs: Long = 0L,
    val questionCount: Int = 0,
    val completedCount: Int = 0,
    val correctCount: Int = 0,
    val submitCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val items: List<ExamPracticeSessionItemAnswerDto> = emptyList()
)

interface ExamPracticeApiService {
    companion object {
        const val PATH_WORD_EXAM = "practice/exam/words/{wordId}"
        const val PATH_FAVORITE = "practice/exam/items/{itemId}/favorite"
        const val PATH_SESSIONS = "practice/exam/sessions"
    }

    @GET(PATH_WORD_EXAM)
    fun getWordPractice(@Path("wordId") wordId: Long): Call<ApiResponse<ExamPracticeWordResponseDto>>

    @PUT(PATH_FAVORITE)
    fun updateFavorite(
        @Path("itemId") itemId: Long,
        @Body request: ExamItemFavoriteRequest
    ): Call<ApiResponse<ExamItemStateDto>>

    @POST(PATH_SESSIONS)
    fun submitSession(@Body request: ExamPracticeSessionSubmitRequest): Call<ApiResponse<Unit>>
}
