package com.chen.memorizewords.data.sync.remoteapi.api.learningsync

import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.PageData
import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = false)
data class PracticeSettingsSyncRequest(
    val selectedBookId: Long,
    val intervalSeconds: Int,
    val loopEnabled: Boolean,
    val showPhonetic: Boolean,
    val showMeaning: Boolean,
    val playbackMode: String = "WORD_ONLY",
    val playTimes: Int = 1,
    val wordRepeatTimes: Int = 1,
    val exampleRepeatTimes: Int = 1,
    val dictationPauseSeconds: Int = 5,
    val revealDelaySeconds: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val timedStopMinutes: Int = 0,
    val keepScreenOn: Boolean = false,
    val playOrder: String = "SEQUENTIAL",
    val provider: String
)

@JsonClass(generateAdapter = false)
data class PracticeSettingsDto(
    val selectedBookId: Long,
    val intervalSeconds: Int,
    val loopEnabled: Boolean,
    val showPhonetic: Boolean,
    val showMeaning: Boolean,
    val playbackMode: String = "WORD_ONLY",
    val playTimes: Int = 1,
    val wordRepeatTimes: Int = 1,
    val exampleRepeatTimes: Int = 1,
    val dictationPauseSeconds: Int = 5,
    val revealDelaySeconds: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val timedStopMinutes: Int = 0,
    val keepScreenOn: Boolean = false,
    val playOrder: String = "SEQUENTIAL",
    val provider: String
)

@JsonClass(generateAdapter = false)
data class LearningEventRequest(
    val clientEventId: String,
    val deviceId: String?,
    val clientSequence: Long?,
    val bookId: Long,
    val wordId: Long,
    val action: String,
    val quality: Int?,
    val correct: Boolean?,
    val businessDate: String,
    val occurredAt: Long,
    val baseStateRevision: Long?,
    val payloadJson: String?,
    val schemaVersion: Int = 1
)

@JsonClass(generateAdapter = false)
data class LearningEventResultDto(
    val eventId: Long?,
    val clientEventId: String,
    val duplicate: Boolean = false,
    val conflict: Boolean = false,
    val message: String? = null,
    val wordState: LearningWordStateDto? = null,
    val learningProgress: LearningProgressDto? = null,
    val wordBookProgress: LearningProgressDto? = null
)

@JsonClass(generateAdapter = false)
data class LearningWordStateDto(
    val wordId: Long,
    val bookId: Long,
    val totalLearnCount: Int,
    val lastLearnTime: Long,
    val nextReviewTime: Long,
    val masteryLevel: Int,
    val userStatus: Int,
    val repetition: Int,
    val interval: Long,
    val efactor: Double,
    val stateRevision: Long = 0L
)

@JsonClass(generateAdapter = false)
data class LearningProgressDto(
    val bookId: Long,
    val bookName: String,
    val learnedCount: Int,
    val masteredCount: Int,
    val totalCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val studyDayCount: Int,
    val lastStudyDate: String? = null,
    val updatedAt: Long = 0L,
    val revision: Long = 0L
)

@JsonClass(generateAdapter = false)
data class PracticeDurationSyncRequest(
    val totalDurationMs: Long,
    val updatedAt: Long
)

@JsonClass(generateAdapter = false)
data class PracticeDurationDto(
    val date: String,
    val totalDurationMs: Long,
    val updatedAt: Long
)

@JsonClass(generateAdapter = false)
data class PracticeSessionSyncRequest(
    val date: String,
    val mode: String,
    val entryType: String,
    val entryCount: Int,
    val durationMs: Long,
    val createdAt: Long,
    val wordIds: List<Long>,
    val questionCount: Int = 0,
    val completedCount: Int = 0,
    val correctCount: Int = 0,
    val submitCount: Int = 0
)

@JsonClass(generateAdapter = false)
data class PracticeSessionDto(
    val id: Long,
    val date: String,
    val mode: String,
    val entryType: String,
    val entryCount: Int,
    val durationMs: Long,
    val createdAt: Long,
    val wordIds: List<Long>,
    val questionCount: Int = 0,
    val completedCount: Int = 0,
    val correctCount: Int = 0,
    val submitCount: Int = 0
)

@JsonClass(generateAdapter = false)
data class FloatingFieldConfigDto(
    val type: String,
    val enabled: Boolean,
    val fontSizeSp: Int
)

@JsonClass(generateAdapter = false)
data class FloatingDockConfigDto(
    val snapTriggerDistanceDp: Int,
    val halfHiddenEnabled: Boolean,
    val allowedEdges: List<String>,
    val edgePriority: List<String>,
    val snapAnimationDurationMs: Long,
    val tapExpandsCardAfterUnsnap: Boolean,
    val initialDockEdge: String
)

@JsonClass(generateAdapter = false)
data class FloatingDockStateDto(
    val dockedEdge: String? = null,
    val crossAxisPercent: Float = 0.5f
)

@JsonClass(generateAdapter = false)
data class FloatingSettingsSyncRequest(
    val enabled: Boolean,
    val sourceType: String,
    val orderType: String,
    val fieldConfigs: List<FloatingFieldConfigDto>,
    val selectedWordIds: List<Long>,
    val floatingBallX: Int,
    val floatingBallY: Int,
    val autoStartOnBoot: Boolean,
    val autoStartOnAppLaunch: Boolean,
    val ballSizePercent: Int,
    val ballOpacityPercent: Int = 100,
    val cardOpacityPercent: Int,
    val cardGapDp: Int,
    val dockConfig: FloatingDockConfigDto? = null,
    val dockState: FloatingDockStateDto? = null
)

@JsonClass(generateAdapter = false)
data class FloatingSettingsDto(
    val enabled: Boolean,
    val sourceType: String,
    val orderType: String,
    val fieldConfigs: List<FloatingFieldConfigDto>,
    val selectedWordIds: List<Long>,
    val floatingBallX: Int,
    val floatingBallY: Int,
    val autoStartOnBoot: Boolean,
    val autoStartOnAppLaunch: Boolean,
    val ballSizePercent: Int,
    val ballOpacityPercent: Int = 100,
    val cardOpacityPercent: Int = 100,
    val cardGapDp: Int,
    val dockConfig: FloatingDockConfigDto? = null,
    val dockState: FloatingDockStateDto? = null
)

@JsonClass(generateAdapter = false)
data class FloatingDisplayRecordSyncRequest(
    val displayCount: Int,
    val wordIds: List<Long>,
    val updatedAt: Long
)

@JsonClass(generateAdapter = false)
data class FloatingDisplayRecordDto(
    val date: String,
    val displayCount: Int,
    val wordIds: List<Long>,
    val updatedAt: Long
)

interface LearningSyncApiService {
    companion object {
        const val PATH_LEARNING_EVENTS = "me/learning-events"
        const val PATH_PRACTICE_SETTINGS = "me/practice/settings"
        const val PATH_PRACTICE_DURATION_ITEM = "me/practice/durations/{date}"
        const val PATH_PRACTICE_SESSIONS = "me/practice/sessions"
        const val PATH_FLOATING_SETTINGS = "me/floating/settings"
        const val PATH_FLOATING_DISPLAY_RECORD_ITEM = "me/floating/display-records/{date}"
    }

    @POST(PATH_LEARNING_EVENTS)
    fun recordLearningEvent(@Body request: LearningEventRequest): Call<ApiResponse<LearningEventResultDto>>

    @PUT(PATH_PRACTICE_SETTINGS)
    fun updatePracticeSettings(@Body request: PracticeSettingsSyncRequest): Call<ApiResponse<Unit>>

    @GET(PATH_PRACTICE_SETTINGS)
    fun getPracticeSettings(): Call<ApiResponse<PracticeSettingsDto?>>

    @PUT(PATH_PRACTICE_DURATION_ITEM)
    fun upsertPracticeDuration(
        @Path("date") date: String,
        @Body request: PracticeDurationSyncRequest
    ): Call<ApiResponse<Unit>>

    @GET("me/practice/durations")
    fun getPracticeDurations(
        @Query("page") page: Int,
        @Query("count") count: Int
    ): Call<ApiResponse<PageData<PracticeDurationDto>>>

    @POST(PATH_PRACTICE_SESSIONS)
    fun appendPracticeSession(@Body request: PracticeSessionSyncRequest): Call<ApiResponse<Unit>>

    @GET(PATH_PRACTICE_SESSIONS)
    fun getPracticeSessions(
        @Query("page") page: Int,
        @Query("count") count: Int
    ): Call<ApiResponse<PageData<PracticeSessionDto>>>

    @PUT(PATH_FLOATING_SETTINGS)
    fun updateFloatingSettings(@Body request: FloatingSettingsSyncRequest): Call<ApiResponse<Unit>>

    @GET(PATH_FLOATING_SETTINGS)
    fun getFloatingSettings(): Call<ApiResponse<FloatingSettingsDto?>>

    @PUT(PATH_FLOATING_DISPLAY_RECORD_ITEM)
    fun upsertFloatingDisplayRecord(
        @Path("date") date: String,
        @Body request: FloatingDisplayRecordSyncRequest
    ): Call<ApiResponse<Unit>>

    @GET("me/floating/display-records")
    fun getFloatingDisplayRecords(
        @Query("page") page: Int,
        @Query("count") count: Int
    ): Call<ApiResponse<PageData<FloatingDisplayRecordDto>>>
}
