package com.chen.memorizewords.domain.study.usecase.word.study

import com.chen.memorizewords.domain.study.model.learning.LearningEventAction
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventCommand
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventResult
import com.chen.memorizewords.domain.study.model.record.CalendarDayStats
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.model.record.DailyStudySummary
import com.chen.memorizewords.domain.study.model.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.model.record.DayCheckInDetail
import com.chen.memorizewords.domain.study.model.record.TodayCheckInEntryState
import com.chen.memorizewords.domain.study.repository.learning.LearningCommandPort
import com.chen.memorizewords.domain.study.repository.record.LearningRecordRepository
import com.chen.memorizewords.domain.study.usecase.learning.RecordLearningEventUseCase
import com.chen.memorizewords.domain.sync.model.LearningPrerequisitesSnapshot
import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import com.chen.memorizewords.domain.sync.model.SyncBannerState
import com.chen.memorizewords.domain.sync.model.SyncPendingRecord
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import com.chen.memorizewords.domain.sync.usecase.TriggerSyncDrainUseCase
import com.chen.memorizewords.domain.word.model.word.Word
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class SetWordAsMasteredUseCaseTest {

    @Test
    fun `records mastered review word as learning event`() = runBlocking {
        val word = testWord()
        val learningCommandPort = FakeLearningCommandPort()
        val learningRecordRepository = FakeLearningRecordRepository()
        val syncRepository = FakeSyncRepository()
        val useCase = SetWordAsMasteredUseCase(
            recordLearningEvent = RecordLearningEventUseCase(
                learningCommandPort = learningCommandPort,
                triggerSyncDrain = TriggerSyncDrainUseCase(syncRepository)
            ),
            getCurrentBusinessDateUseCase = GetCurrentBusinessDateUseCase(learningRecordRepository)
        )

        useCase(bookId = 10L, word = word, isNewWord = false)

        val command = learningCommandPort.recordedCommand!!
        assertEquals(10L, command.bookId)
        assertEquals(word, command.word)
        assertEquals(LearningEventAction.MASTERED, command.action)
        assertEquals(5, command.quality)
        assertFalse(command.isNewWordOverride!!)
        assertEquals("2026-06-23", command.businessDate)
        assertEquals("""{"isNewWord":false}""", command.payloadJson)
        assertTrue(syncRepository.triggered)
    }

    private class FakeLearningCommandPort : LearningCommandPort {
        var recordedCommand: RecordLearningEventCommand? = null

        override suspend fun record(command: RecordLearningEventCommand): RecordLearningEventResult {
            recordedCommand = command
            return RecordLearningEventResult(
                clientEventId = "event-1",
                wordId = command.word.id,
                bookId = command.bookId,
                stateRevision = 1L,
                progressRevision = 1L
            )
        }
    }

    private class FakeLearningRecordRepository : LearningRecordRepository {
        override fun getCurrentBusinessDate(): String = "2026-06-23"

        override suspend fun addStudyDuration(durationMs: Long) = Unit
        override fun getStudyTotalDayCount(): Flow<Int> = emptyFlow()
        override fun getContinuousCheckInDays(): Flow<Int> = emptyFlow()
        override fun getTodayNewWordCount(): Flow<Int> = emptyFlow()
        override fun getTodayReviewWordCount(): Flow<Int> = emptyFlow()
        override fun getTodayStudyDurationMs(): Flow<Long> = emptyFlow()
        override fun getStudyTotalDurationMs(): Flow<Long> = emptyFlow()
        override fun getDailyWordStats(startDate: String, endDate: String): Flow<List<DailyWordStats>> = emptyFlow()
        override fun getDailyDurationStats(startDate: String, endDate: String): Flow<List<DailyDurationStats>> = emptyFlow()
        override fun getCalendarDayStats(startDate: String, endDate: String): Flow<List<CalendarDayStats>> = emptyFlow()
        override fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecord>> = emptyFlow()
        override fun getDailyStudySummary(date: String): Flow<DailyStudySummary> = emptyFlow()
        override fun getDayCheckInDetail(date: String): Flow<DayCheckInDetail> = emptyFlow()
        override suspend fun getTodayCheckInEntryState(): TodayCheckInEntryState = error("Not needed")
        override suspend fun makeUpCheckIn(date: String): Result<CheckInRecord> = error("Not needed")
        override suspend fun autoCheckInTodayIfEligible(): Result<CheckInRecord?> = error("Not needed")
    }

    private class FakeSyncRepository : SyncRepository {
        var triggered = false

        override fun observePostLoginBootstrapState(): Flow<PostLoginBootstrapState> =
            flowOf(PostLoginBootstrapState.Idle)

        override fun getCurrentPostLoginBootstrapState(): PostLoginBootstrapState =
            PostLoginBootstrapState.Idle

        override fun startPostLoginBootstrap() = Unit

        override suspend fun syncAfterLogin(): Result<Unit> = Result.success(Unit)

        override suspend fun restoreLearningPrerequisites(): Result<LearningPrerequisitesSnapshot> =
            Result.failure(UnsupportedOperationException("Not needed"))

        override suspend fun discardLocalPendingSyncOnLogin() = Unit

        override fun observePendingSyncCount(): Flow<Int> = flowOf(0)

        override fun observePendingSyncRecords(): Flow<List<SyncPendingRecord>> = flowOf(emptyList())

        override fun observeSyncBannerState(): Flow<SyncBannerState> = flowOf(SyncBannerState.Hidden)

        override fun triggerDrain() {
            triggered = true
        }

        override fun scheduleBootstrapSync() = Unit
    }
}

private fun testWord(): Word {
    return Word(
        id = 100L,
        word = "test",
        normalizedWord = "test",
        phoneticUS = null,
        phoneticUK = null,
        hasIrregularForms = false,
        memoryTip = null,
        mnemonicImageUrl = null,
        memoryAssociations = emptyList(),
        wordFamily = null,
        synonyms = emptyList(),
        antonyms = emptyList(),
        tags = emptyList(),
        notes = null,
        rootMemoryTip = null
    )
}
