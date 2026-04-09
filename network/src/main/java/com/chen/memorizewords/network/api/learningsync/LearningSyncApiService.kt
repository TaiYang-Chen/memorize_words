package com.chen.memorizewords.network.api.learningsync

import com.chen.memorizewords.network.model.ApiResponse
import com.chen.memorizewords.network.model.PageData
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
    val playWordSpelling: Boolean,
    val playChineseMeaning: Boolean,
    val provider: String
)

@JsonClass(generateAdapter = false)
data class PracticeSettingsDto(
    val selectedBookId: Long,
    val intervalSeconds: Int,
    val loopEnabled: Boolean,
    val playWordSpelling: Boolean,
    val playChineseMeaning: Boolean,
    val provider: String
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
    val cardOpacityPercent: Int,
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
    val cardOpacityPercent: Int = 100,
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
        const val PATH_PRACTICE_SETTINGS = "me/practice/settings"
        const val PATH_PRACTICE_DURATION_ITEM = "me/practice/durations/{date}"
        const val PATH_PRACTICE_SESSIONS = "me/practice/sessions"
        const val PATH_FLOATING_SETTINGS = "me/floating/settings"
        const val PATH_FLOATING_DISPLAY_RECORD_ITEM = "me/floating/display-records/{date}"
    }

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
