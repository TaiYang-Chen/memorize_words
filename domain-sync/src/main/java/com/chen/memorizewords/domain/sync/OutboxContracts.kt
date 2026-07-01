package com.chen.memorizewords.domain.sync

import kotlinx.coroutines.flow.Flow

object OutboxTopic {
    const val STUDY_RECORD = "STUDY_RECORD"
    const val DAILY_STUDY_DURATION = "DAILY_STUDY_DURATION"
    const val PRACTICE_DURATION = "PRACTICE_DURATION"
    const val PRACTICE_SESSION = "PRACTICE_SESSION"
    const val FAVORITE = "FAVORITE"
    const val WORD_BOOK_PROGRESS = "WORD_BOOK_PROGRESS"
    const val WORD_BOOK_DELETE = "WORD_BOOK_DELETE"
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

const val OUTBOX_PAYLOAD_SCHEMA_VERSION = 1

data class OutboxCommand(
    val topic: String,
    val key: String,
    val operation: SyncOperation,
    val payload: String,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

enum class SyncFailureKind {
    NETWORK,
    AUTH,
    SERVER,
    RATE_LIMIT,
    CLIENT,
    CONFLICT,
    UNKNOWN
}

data class SyncFailureDecision(
    val shouldRetry: Boolean,
    val failureKind: SyncFailureKind,
    val persistedMessage: String
)

interface SyncOutboxWriter {
    suspend fun enqueueLatest(command: OutboxCommand)

    suspend fun enqueueLatest(
        bizType: String,
        bizKey: String,
        operation: SyncOperation,
        payload: String,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        enqueueLatest(
            OutboxCommand(
                topic = bizType,
                key = bizKey,
                operation = operation,
                payload = payload,
                updatedAtEpochMillis = updatedAt
            )
        )
    }
}

interface SyncOutboxReader {
    fun observeByTopic(topic: String): Flow<List<OutboxRecord>>
    suspend fun getByTopic(topic: String): List<OutboxRecord>
}

interface SyncOutboxHandler {
    val topics: Set<String>
    suspend fun handle(record: OutboxRecord)
    suspend fun onSuccess(record: OutboxRecord) = Unit
}

interface SyncRemoteAdapter {
    val topic: String
    suspend fun push(record: OutboxRecord): Result<Unit>
    suspend fun pullServerSnapshot(record: OutboxRecord): Result<Unit> = Result.success(Unit)
}

interface SyncConflictPolicy {
    fun decide(throwable: Throwable?): SyncFailureDecision
}

interface ServerBootstrapContributor {
    val bootstrapKey: String
    suspend fun bootstrapFromServer(): Result<Unit>
}

data class StudyRecordSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
    val date: String,
    val wordId: Long,
    val word: String,
    val definition: String,
    val isNewWord: Boolean
)

data class DailyStudyDurationSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
    val date: String,
    val totalDurationMs: Long,
    val updatedAt: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)

data class PracticeDurationSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
    val date: String,
    val totalDurationMs: Long,
    val updatedAt: Long
)

data class PracticeSessionSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
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
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
    val wordId: Long,
    val word: String? = null,
    val definitions: String? = null,
    val phonetic: String? = null,
    val addedDate: String? = null,
    val addedAt: Long? = null
)

data class WordBookProgressSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
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

data class WordBookDeleteSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
    val bookId: Long
)

data class WordBookSelectionSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
    val bookId: Long
)

data class WordStateUpsertSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
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
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
    val bookId: Long
)

data class StudyPlanSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
    val dailyNewWords: Int,
    val dailyReviewWords: Int,
    val testMode: String,
    val wordOrderType: String
)

data class OnboardingStateSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
    val phase: String,
    val selectedWordBookId: Long?,
    val revision: Long,
    val updatedAt: Long,
    val completedAt: Long?
)

data class PracticeSettingsSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
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
    val provider: String = "BAIDU"
)

data class FloatingSettingsSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
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
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
    val date: String,
    val displayCount: Int,
    val wordIds: List<Long>,
    val updatedAt: Long
)

data class CheckInRecordSyncPayload(
    val schemaVersion: Int = OUTBOX_PAYLOAD_SCHEMA_VERSION,
    val date: String,
    val type: String,
    val signedAt: Long,
    val updatedAt: Long
)
