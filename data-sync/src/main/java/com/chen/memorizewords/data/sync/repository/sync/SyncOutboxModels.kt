package com.chen.memorizewords.data.sync.repository.sync

object SyncOutboxBizType {
    const val STUDY_RECORD = "STUDY_RECORD"
    const val DAILY_STUDY_DURATION = "DAILY_STUDY_DURATION"
    const val PRACTICE_DURATION = "PRACTICE_DURATION"
    const val PRACTICE_SESSION = "PRACTICE_SESSION"
    const val FAVORITE = "FAVORITE"
    const val WORD_BOOK_PROGRESS = "WORD_BOOK_PROGRESS"
    const val WORD_STATE_UPSERT = "WORD_STATE_UPSERT"
    const val WORD_STATE_DELETE_BY_BOOK = "WORD_STATE_DELETE_BY_BOOK"
    const val WORD_BOOK_SELECTION = "WORD_BOOK_SELECTION"
    const val STUDY_PLAN = "STUDY_PLAN"
    const val ONBOARDING_STATE = "ONBOARDING_STATE"
    const val PRACTICE_SETTINGS = "PRACTICE_SETTINGS"
    const val FLOATING_SETTINGS = "FLOATING_SETTINGS"
    const val FLOATING_DISPLAY_RECORD = "FLOATING_DISPLAY_RECORD"
    const val CHECKIN_RECORD = "CHECKIN_RECORD"
}

enum class SyncOutboxOperation {
    UPSERT,
    DELETE
}

enum class SyncOutboxState {
    QUEUED,
    IN_FLIGHT,
    RETRY_WAITING,
    BLOCKED
}

enum class SyncOutboxFailureKind {
    NETWORK,
    AUTH,
    SERVER,
    RATE_LIMIT,
    CLIENT,
    CONFLICT,
    UNKNOWN
}

data class StudyRecordSyncPayload(
    val date: String,
    val wordId: Long,
    val word: String,
    val definition: String,
    val isNewWord: Boolean
)

data class DailyStudyDurationSyncPayload(
    val date: String,
    val totalDurationMs: Long,
    val updatedAt: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)

data class PracticeDurationSyncPayload(
    val date: String,
    val totalDurationMs: Long,
    val updatedAt: Long
)

data class PracticeSessionSyncPayload(
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

data class FavoriteSyncPayload(
    val wordId: Long,
    val word: String? = null,
    val definitions: String? = null,
    val phonetic: String? = null,
    val addedDate: String? = null,
    val addedAt: Long? = null
)

data class WordBookProgressSyncPayload(
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

data class WordBookSelectionSyncPayload(
    val bookId: Long
)

data class WordStateUpsertSyncPayload(
    val bookId: Long,
    val wordId: Long,
    val totalLearnCount: Int,
    val lastLearnTime: Long,
    val nextReviewTime: Long,
    val masteryLevel: Int,
    val userStatus: Int,
    val repetition: Int,
    val interval: Long,
    val efactor: Double
)

data class WordStateDeleteByBookSyncPayload(
    val bookId: Long
)

data class StudyPlanSyncPayload(
    val dailyNewWords: Int,
    val dailyReviewWords: Int,
    val testMode: String,
    val wordOrderType: String
)

data class OnboardingStateSyncPayload(
    val phase: String,
    val selectedWordBookId: Long?,
    val revision: Long,
    val updatedAt: Long,
    val completedAt: Long?
)

data class PracticeSettingsSyncPayload(
    val selectedBookId: Long,
    val intervalSeconds: Int,
    val loopEnabled: Boolean,
    val showPhonetic: Boolean,
    val showMeaning: Boolean,
    val playbackMode: String = "WORD_ONLY",
    val playTimes: Int = 1,
    val provider: String = "BAIDU"
)

data class FloatingSettingsSyncPayload(
    val enabled: Boolean,
    val sourceType: String,
    val orderType: String,
    val fieldConfigsJson: String,
    val selectedWordIdsJson: String,
    val floatingBallX: Int,
    val floatingBallY: Int,
    val autoStartOnBoot: Boolean,
    val autoStartOnAppLaunch: Boolean,
    val ballSizePercent: Int? = null,
    val ballOpacityPercent: Int,
    val cardOpacityPercent: Int,
    val cardGapDp: Int = 40,
    val dockConfigJson: String? = null,
    val dockStateJson: String? = null
)

data class FloatingDisplayRecordSyncPayload(
    val date: String,
    val displayCount: Int,
    val wordIds: List<Long>,
    val updatedAt: Long
)

data class CheckInRecordSyncPayload(
    val date: String,
    val type: String,
    val signedAt: Long,
    val updatedAt: Long
)
