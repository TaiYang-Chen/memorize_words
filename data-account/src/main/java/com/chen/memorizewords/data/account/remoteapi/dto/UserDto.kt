package com.chen.memorizewords.data.account.remoteapi.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class LoginResponseDto(
    val token: String,
    val refreshToken: String,
    val tokenType: String,
    val user: UserDto,
    val expiresIn: Long,
    val refreshTokenExpiresIn: Long,
    val onboarding: OnboardingStateDto? = null,
    val bootstrap: LoginBootstrapDto? = null
)

@JsonClass(generateAdapter = false)
data class UserDto(
    val id: Long,
    val account: String? = null,
    val email: String?,
    val nickname: String?,
    val gender: String?,
    val avatarUrl: String?,
    val phone: String?,
    val qq: String?,
    val wechat: String?,
    val emailVerified: Boolean,
    val onboardingCompleted: Boolean? = null
)

@JsonClass(generateAdapter = false)
data class OnboardingStateDto(
    val phase: String,
    val selectedWordBookId: Long?,
    val revision: Long,
    val updatedAtMs: Long,
    val completedAtMs: Long?
)

@JsonClass(generateAdapter = false)
data class LoginBootstrapDto(
    val version: Int = 1,
    val serverTime: Long = 0L,
    val businessDate: String? = null,
    val onboarding: OnboardingStateDto? = null,
    val studyPlan: StudyPlanDto? = null,
    val currentWordBook: WordBookDto? = null,
    val currentWordBookProgress: WordBookProgressDto? = null,
    val todayStats: TodayStudyStatsDto? = null,
    val todayStudyRecords: List<StudyRecordDto> = emptyList(),
    val todayStudyDuration: DailyStudyDurationDto? = null,
    val checkInStatus: CheckInStatusDto? = null,
    val currentWordBookContentVersion: Long? = null
)

@JsonClass(generateAdapter = false)
data class StudyPlanDto(
    val dailyNewWords: Int,
    val dailyReviewWords: Int,
    val testMode: String = "MEANING_CHOICE",
    val wordOrderType: String = "RANDOM"
)

@JsonClass(generateAdapter = false)
data class WordBookDto(
    val id: Long,
    val title: String,
    val category: String,
    val imgUrl: String,
    val description: String,
    val totalWords: Int,
    val learnedWords: Int = 0,
    val contentVersion: Long = 0L,
    val contentPackage: WordBookContentPackageDto? = null,
    val updatedAtMs: Long = 0L,
    val isNew: Boolean = false,
    val isHot: Boolean = false,
    val isSelected: Boolean = false,
    val isPublic: Boolean = true,
    val createdByUserId: String? = null
)

@JsonClass(generateAdapter = false)
data class WordBookContentPackageDto(
    val url: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0L,
    val contentType: String = "",
    val schemaVersion: Int = 0,
    val contentVersion: Long = 0L
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
    val lastStudyDate: String? = null,
    val updatedAtMs: Long = 0L,
    val revision: Long = 0L
)

@JsonClass(generateAdapter = false)
data class TodayStudyStatsDto(
    val date: String,
    val newWordCount: Int = 0,
    val reviewWordCount: Int = 0,
    val studyDurationMs: Long = 0L,
    val totalStudyDayCount: Int = 0,
    val continuousCheckInDays: Int = 0
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
data class DailyStudyDurationDto(
    val date: String,
    val totalDurationMs: Long,
    val updatedAtMs: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)

@JsonClass(generateAdapter = false)
data class CheckInStatusDto(
    val continuousCheckInDays: Int,
    val lastCheckInDate: String?,
    val makeupCardBalance: Int = 0
)

@JsonClass(generateAdapter = false)
data class SendSmsCodeResponseDto(
    val expireSeconds: Int,
    val resendIntervalSeconds: Int
)
