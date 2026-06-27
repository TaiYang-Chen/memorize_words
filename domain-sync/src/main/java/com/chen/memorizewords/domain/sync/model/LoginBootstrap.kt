package com.chen.memorizewords.domain.sync.model

import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress

data class LoginBootstrap(
    val version: Int = 1,
    val serverTime: Long = 0L,
    val businessDate: String? = null,
    val onboarding: OnboardingSnapshot? = null,
    val studyPlan: StudyPlan? = null,
    val currentWordBook: WordBook? = null,
    val currentWordBookProgress: WordBookProgress? = null,
    val todayStats: LoginBootstrapTodayStats? = null,
    val todayStudyRecords: List<LoginBootstrapStudyRecord> = emptyList(),
    val todayStudyDuration: LoginBootstrapDailyStudyDuration? = null,
    val checkInStatus: LoginBootstrapCheckInStatus? = null,
    val currentWordBookContentVersion: Long? = null
)

data class LoginBootstrapTodayStats(
    val date: String,
    val newWordCount: Int = 0,
    val reviewWordCount: Int = 0,
    val studyDurationMs: Long = 0L,
    val totalStudyDayCount: Int = 0,
    val continuousCheckInDays: Int = 0
)

data class LoginBootstrapStudyRecord(
    val date: String,
    val wordId: Long,
    val word: String,
    val definition: String,
    val isNewWord: Boolean
)

data class LoginBootstrapDailyStudyDuration(
    val date: String,
    val totalDurationMs: Long,
    val updatedAt: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)

data class LoginBootstrapCheckInStatus(
    val continuousCheckInDays: Int,
    val lastCheckInDate: String?,
    val makeupCardBalance: Int = 0
)
