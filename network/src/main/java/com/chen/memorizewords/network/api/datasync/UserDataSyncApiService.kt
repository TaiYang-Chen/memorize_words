package com.chen.memorizewords.network.api.datasync

import com.chen.memorizewords.network.dto.wordbook.WordBookDto
import com.chen.memorizewords.network.dto.wordstate.WordStateDto
import com.chen.memorizewords.network.model.ApiResponse
import com.chen.memorizewords.network.model.PageData
import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = false)
data class StudyPlanDto(
    val dailyNewWords: Int,
    val dailyReviewWords: Int,
    val testMode: String = "MEANING_CHOICE",
    val wordOrderType: String = "RANDOM"
)

@JsonClass(generateAdapter = false)
data class AddMyWordBookRequest(
    val bookId: Long
)

@JsonClass(generateAdapter = false)
data class FavoriteSyncRequest(
    val wordId: Long,
    val word: String,
    val definitions: String,
    val phonetic: String?,
    val addedDate: String
)

@JsonClass(generateAdapter = false)
data class FavoriteDto(
    val wordId: Long,
    val word: String,
    val definitions: String,
    val phonetic: String?,
    val addedDate: String
)

@JsonClass(generateAdapter = false)
data class WordStateSyncRequest(
    val totalLearnCount: Int,
    val lastLearnTime: Long,
    val nextReviewTime: Long,
    val masteryLevel: Int,
    val userStatus: Int,
    val repetition: Int,
    val interval: Long,
    val efactor: Double
)

@JsonClass(generateAdapter = false)
data class WordBookProgressSyncRequest(
    val bookName: String,
    val learnedCount: Int,
    val masteredCount: Int,
    val totalCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val studyDayCount: Int,
    val lastStudyDate: String
)

@JsonClass(generateAdapter = false)
data class WordBookProgressDto(
    val bookId: Long,
    val bookName: String,
    val learnedCount: Int,
    val masteredCount: Int,
    val totalCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val studyDayCount: Int,
    val lastStudyDate: String
)

@JsonClass(generateAdapter = false)
data class WordBookUpdateSummaryDto(
    val addedCount: Int,
    val modifiedCount: Int,
    val removedCount: Int,
    val sampleWords: List<String> = emptyList()
)

@JsonClass(generateAdapter = false)
data class PendingWordBookUpdateDto(
    val bookId: Long,
    val bookName: String,
    val currentVersion: Long,
    val targetVersion: Long,
    val publishedAt: Long,
    val summary: WordBookUpdateSummaryDto,
    val applyMode: String,
    val changeSummaryText: String = "",
    val versionScope: String = "",
    val detailAvailable: Boolean = true
)

@JsonClass(generateAdapter = false)
data class WordBookUpdateManifestDto(
    val bookId: Long,
    val targetVersion: Long,
    val applyMode: String,
    val removedWordIds: List<Long> = emptyList(),
    val upsertWordCount: Int = 0,
    val pageSize: Int = 50,
    val changeSummaryText: String = "",
    val versionScope: String = "",
    val detailAvailable: Boolean = true
)

@JsonClass(generateAdapter = false)
data class WordBookUpdateCandidateDto(
    val bookId: Long,
    val bookName: String,
    val currentVersion: Long,
    val targetVersion: Long,
    val publishedAt: Long,
    val summary: WordBookUpdateSummaryDto,
    val applyMode: String,
    val importance: String = "NORMAL",
    val detailAvailable: Boolean = false,
    val estimatedDownloadBytes: Long = 0L,
    val forcePrompt: Boolean = false,
    val silentAllowed: Boolean = false,
    val changeSummaryText: String = "",
    val versionScope: String = ""
)

@JsonClass(generateAdapter = false)
data class WordBookUpdateActionRequest(
    val action: String,
    val bookId: Long? = null,
    val targetVersion: Long? = null,
    val trigger: String? = null,
    val executionMode: String? = null,
    val deferredUntil: Long? = null,
    val failureReason: String? = null
)

@JsonClass(generateAdapter = false)
data class StudyRecordSyncRequest(
    val date: String,
    val wordId: Long,
    val word: String,
    val definition: String,
    val isNewWord: Boolean
)

@JsonClass(generateAdapter = false)
data class StudyRecordDto(
    val date: String,
    val wordId: Long,
    val word: String,
    val definition: String,
    val isNewWord: Boolean
)

@JsonClass(generateAdapter = false)
data class DailyStudyDurationSyncRequest(
    val totalDurationMs: Long,
    val updatedAt: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)

@JsonClass(generateAdapter = false)
data class DailyStudyDurationDto(
    val date: String,
    val totalDurationMs: Long,
    val updatedAt: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)

@JsonClass(generateAdapter = false)
data class WordBookSelectionSyncRequest(
    val selected: Boolean = true
)

@JsonClass(generateAdapter = false)
data class CheckInConfigDto(
    val dayBoundaryOffsetMinutes: Int,
    val timezoneId: String
)

@JsonClass(generateAdapter = false)
data class CheckInStatusDto(
    val continuousCheckInDays: Int,
    val lastCheckInDate: String?,
    val makeupCardBalance: Int = 0
)

@JsonClass(generateAdapter = false)
data class CheckInRecordSyncRequest(
    val type: String,
    val signedAt: Long,
    val updatedAt: Long
)

@JsonClass(generateAdapter = false)
data class CheckInRecordDto(
    val date: String,
    val type: String,
    val signedAt: Long,
    val updatedAt: Long
)

interface UserDataSyncApiService {
    companion object {
        const val PATH_STUDY_PLAN = "me/study-plan"
        const val PATH_MY_WORD_BOOKS = "me/wordbooks"
        const val PATH_WORD_STATES = "me/wordbooks/{bookId}/word-states"
        const val PATH_WORD_STATE_ITEM = "me/wordbooks/{bookId}/word-states/{wordId}"
        const val PATH_WORD_BOOK_PROGRESS = "me/wordbooks/{bookId}/progress"
        const val PATH_WORD_BOOK_PROGRESS_LIST = "me/wordbooks/progress"
        const val PATH_WORD_BOOK_PENDING_UPDATE = "me/wordbooks/{bookId}/updates/pending"
        const val PATH_WORD_BOOK_UPDATE_IGNORE = "me/wordbooks/{bookId}/updates/{version}/ignore"
        const val PATH_WORD_BOOK_UPDATE_MANIFEST = "me/wordbooks/{bookId}/updates/{version}/manifest"
        const val PATH_WORD_BOOK_UPDATE_WORDS = "me/wordbooks/{bookId}/updates/{version}/words"
        const val PATH_WORD_BOOK_UPDATE_COMPLETE = "me/wordbooks/{bookId}/updates/{version}/complete"
        const val PATH_CURRENT_WORD_BOOK_UPDATE_CANDIDATE = "me/wordbooks/current/update-candidate"
        const val PATH_CURRENT_WORD_BOOK_UPDATE_ACTIONS = "me/wordbooks/current/update-actions"
        const val PATH_CURRENT_WORD_BOOK_UPDATE_MANIFEST = "me/wordbooks/current/updates/{version}/manifest"
        const val PATH_CURRENT_WORD_BOOK_UPDATE_WORDS = "me/wordbooks/current/updates/{version}/words"
        const val PATH_CURRENT_WORD_BOOK_UPDATE_COMPLETE = "me/wordbooks/current/updates/{version}/complete"
        const val PATH_STUDY_RECORDS = "me/study-records"
        const val PATH_STUDY_DURATION = "me/study-duration"
        const val PATH_STUDY_DURATION_ITEM = "me/study-duration/{date}"
        const val PATH_WORD_BOOK_SELECTION = "me/wordbooks/{bookId}/selection"
        const val PATH_FAVORITES = "me/favorites"
        const val PATH_FAVORITE_ITEM = "me/favorites/{wordId}"
        const val PATH_CHECKIN_CONFIG = "me/checkin/config"
        const val PATH_CHECKIN_STATUS = "me/checkin/status"
        const val PATH_CHECKIN_RECORDS = "me/checkin/records"
        const val PATH_CHECKIN_RECORD_ITEM = "me/checkin/records/{date}"
    }

    @GET(PATH_STUDY_PLAN)
    fun getStudyPlan(): Call<ApiResponse<StudyPlanDto?>>

    @PUT(PATH_STUDY_PLAN)
    fun updateStudyPlan(@Body request: StudyPlanDto): Call<ApiResponse<Unit>>

    @GET(PATH_MY_WORD_BOOKS)
    fun getMyWordBooks(): Call<ApiResponse<List<WordBookDto>>>

    @POST(PATH_MY_WORD_BOOKS)
    fun addMyWordBook(@Body request: AddMyWordBookRequest): Call<ApiResponse<Unit>>

    @GET(PATH_WORD_STATES)
    fun getWordStates(
        @Path("bookId") bookId: Long,
        @Query("page") page: Int,
        @Query("count") count: Int
    ): Call<ApiResponse<PageData<WordStateDto>>>

    @POST(PATH_FAVORITES)
    fun addFavorite(@Body request: FavoriteSyncRequest): Call<ApiResponse<Unit>>

    @GET(PATH_FAVORITES)
    fun getFavorites(
        @Query("page") page: Int,
        @Query("count") count: Int
    ): Call<ApiResponse<PageData<FavoriteDto>>>

    @DELETE(PATH_FAVORITE_ITEM)
    fun removeFavorite(@Path("wordId") wordId: Long): Call<ApiResponse<Unit>>

    @PUT(PATH_WORD_STATE_ITEM)
    fun upsertWordState(
        @Path("bookId") bookId: Long,
        @Path("wordId") wordId: Long,
        @Body request: WordStateSyncRequest
    ): Call<ApiResponse<Unit>>

    @DELETE(PATH_WORD_STATES)
    fun deleteWordStatesByBookId(@Path("bookId") bookId: Long): Call<ApiResponse<Unit>>

    @PUT(PATH_WORD_BOOK_PROGRESS)
    fun upsertWordBookProgress(
        @Path("bookId") bookId: Long,
        @Body request: WordBookProgressSyncRequest
    ): Call<ApiResponse<Unit>>

    @GET(PATH_WORD_BOOK_PROGRESS_LIST)
    fun getWordBookProgressList(): Call<ApiResponse<List<WordBookProgressDto>>>

    @GET(PATH_WORD_BOOK_PENDING_UPDATE)
    fun getPendingWordBookUpdate(
        @Path("bookId") bookId: Long
    ): Call<ApiResponse<PendingWordBookUpdateDto?>>

    @POST(PATH_WORD_BOOK_UPDATE_IGNORE)
    fun ignoreWordBookUpdate(
        @Path("bookId") bookId: Long,
        @Path("version") version: Long
    ): Call<ApiResponse<Unit>>

    @GET(PATH_WORD_BOOK_UPDATE_MANIFEST)
    fun getWordBookUpdateManifest(
        @Path("bookId") bookId: Long,
        @Path("version") version: Long
    ): Call<ApiResponse<WordBookUpdateManifestDto>>

    @GET(PATH_WORD_BOOK_UPDATE_WORDS)
    fun getWordBookUpdateWords(
        @Path("bookId") bookId: Long,
        @Path("version") version: Long,
        @Query("page") page: Int,
        @Query("count") count: Int
    ): Call<ApiResponse<PageData<com.chen.memorizewords.network.dto.wordbook.WordDto>>>

    @POST(PATH_WORD_BOOK_UPDATE_COMPLETE)
    fun completeWordBookUpdate(
        @Path("bookId") bookId: Long,
        @Path("version") version: Long
    ): Call<ApiResponse<Unit>>

    @GET(PATH_CURRENT_WORD_BOOK_UPDATE_CANDIDATE)
    fun getCurrentWordBookUpdateCandidate(
        @Query("trigger") trigger: String
    ): Call<ApiResponse<WordBookUpdateCandidateDto?>>

    @POST(PATH_CURRENT_WORD_BOOK_UPDATE_ACTIONS)
    fun reportCurrentWordBookUpdateAction(
        @Body request: WordBookUpdateActionRequest
    ): Call<ApiResponse<Unit>>

    @GET(PATH_CURRENT_WORD_BOOK_UPDATE_MANIFEST)
    fun getCurrentWordBookUpdateManifest(
        @Path("version") version: Long
    ): Call<ApiResponse<WordBookUpdateManifestDto>>

    @GET(PATH_CURRENT_WORD_BOOK_UPDATE_WORDS)
    fun getCurrentWordBookUpdateWords(
        @Path("version") version: Long,
        @Query("page") page: Int,
        @Query("count") count: Int
    ): Call<ApiResponse<PageData<com.chen.memorizewords.network.dto.wordbook.WordDto>>>

    @POST(PATH_CURRENT_WORD_BOOK_UPDATE_COMPLETE)
    fun completeCurrentWordBookUpdate(
        @Path("version") version: Long
    ): Call<ApiResponse<Unit>>

    @POST(PATH_STUDY_RECORDS)
    fun appendStudyRecord(@Body request: StudyRecordSyncRequest): Call<ApiResponse<Unit>>

    @GET(PATH_STUDY_RECORDS)
    fun getStudyRecords(
        @Query("page") page: Int,
        @Query("count") count: Int
    ): Call<ApiResponse<PageData<StudyRecordDto>>>

    @GET(PATH_STUDY_DURATION)
    fun getDailyStudyDurations(
        @Query("page") page: Int,
        @Query("count") count: Int
    ): Call<ApiResponse<PageData<DailyStudyDurationDto>>>

    @PUT(PATH_STUDY_DURATION_ITEM)
    fun upsertDailyStudyDuration(
        @Path("date") date: String,
        @Body request: DailyStudyDurationSyncRequest
    ): Call<ApiResponse<Unit>>

    @PUT(PATH_WORD_BOOK_SELECTION)
    fun setCurrentWordBookSelection(
        @Path("bookId") bookId: Long,
        @Body request: WordBookSelectionSyncRequest
    ): Call<ApiResponse<Unit>>

    @GET(PATH_CHECKIN_CONFIG)
    fun getCheckInConfig(): Call<ApiResponse<CheckInConfigDto?>>

    @PUT(PATH_CHECKIN_CONFIG)
    fun updateCheckInConfig(@Body request: CheckInConfigDto): Call<ApiResponse<Unit>>

    @GET(PATH_CHECKIN_STATUS)
    fun getCheckInStatus(): Call<ApiResponse<CheckInStatusDto?>>

    @GET(PATH_CHECKIN_RECORDS)
    fun getCheckInRecords(
        @Query("page") page: Int,
        @Query("count") count: Int
    ): Call<ApiResponse<PageData<CheckInRecordDto>>>

    @PUT(PATH_CHECKIN_RECORD_ITEM)
    fun upsertCheckInRecord(
        @Path("date") date: String,
        @Body request: CheckInRecordSyncRequest
    ): Call<ApiResponse<Unit>>
}
