package com.chen.memorizewords.data.repository.sync

import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxEntity

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
    const val PRACTICE_SETTINGS = "PRACTICE_SETTINGS"
    const val FLOATING_SETTINGS = "FLOATING_SETTINGS"
    const val FLOATING_DISPLAY_RECORD = "FLOATING_DISPLAY_RECORD"
    const val CHECKIN_RECORD = "CHECKIN_RECORD"
}

object SyncOutboxOperation {
    const val UPSERT = "UPSERT"
    const val DELETE = "DELETE"
}

object SyncOutboxState {
    const val PENDING = "PENDING"
    const val SYNCING = "SYNCING"
    const val FAILED = "FAILED"
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
    val addedDate: String? = null
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

data class PracticeSettingsSyncPayload(
    val selectedBookId: Long,
    val intervalSeconds: Int,
    val loopEnabled: Boolean,
    val playWordSpelling: Boolean,
    val playChineseMeaning: Boolean,
    val provider: String = "BACKEND_DEFAULT"
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
    val cardOpacityPercent: Int,
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

fun syncOutboxEntity(
    bizType: String,
    bizKey: String,
    operation: String,
    payload: String,
    updatedAt: Long = System.currentTimeMillis()
): SyncOutboxEntity {
    return SyncOutboxEntity(
        bizType = bizType,
        bizKey = bizKey,
        operation = operation,
        payload = payload,
        state = SyncOutboxState.PENDING,
        retryCount = 0,
        lastError = null,
        updatedAt = updatedAt
    )
}
