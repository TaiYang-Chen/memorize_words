package com.chen.memorizewords.domain.study.usecase.word.study

import com.chen.memorizewords.domain.study.model.learning.DailyPlanTargets
import com.chen.memorizewords.domain.study.model.learning.DailyProgressEvaluation
import com.chen.memorizewords.domain.study.model.learning.DailyProgressProjectionTask
import com.chen.memorizewords.domain.study.model.learning.DailyProgressTransition
import com.chen.memorizewords.domain.study.model.learning.DailyWordCounts
import com.chen.memorizewords.domain.study.model.learning.LearningEventAction
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventCommand
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventResult
import com.chen.memorizewords.domain.study.model.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.repository.learning.LearningCommandPort
import com.chen.memorizewords.domain.study.repository.record.BusinessDateProvider
import com.chen.memorizewords.domain.study.repository.record.DailyStudyProjectionQueue
import com.chen.memorizewords.domain.study.repository.record.DailyStudyProjectionStore
import com.chen.memorizewords.domain.study.repository.record.StudyRecordQuery
import com.chen.memorizewords.domain.study.service.DailyProgressWriteCoordinator
import com.chen.memorizewords.domain.study.service.DailyStudyProjectionCoordinator
import com.chen.memorizewords.domain.study.usecase.learning.RecordLearningEventUseCase
import com.chen.memorizewords.domain.sync.model.LearningPrerequisitesSnapshot
import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import com.chen.memorizewords.domain.sync.model.SyncBannerState
import com.chen.memorizewords.domain.sync.model.SyncPendingRecord
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import com.chen.memorizewords.domain.sync.usecase.TriggerSyncDrainUseCase
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
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
    fun `records mastered review word with plan snapshot and projects it`() = runBlocking {
        val word = testWord()
        val learningCommandPort = FakeLearningCommandPort()
        val syncRepository = FakeSyncRepository()
        val planRepository = FakeStudyPlanRepository()
        val writeCoordinator = DailyProgressWriteCoordinator()
        val projectionStore = FakeProjectionStore()
        val projectionCoordinator = DailyStudyProjectionCoordinator(
            writeCoordinator = writeCoordinator,
            projectionQueue = FakeProjectionQueue(learningCommandPort),
            projectionStore = projectionStore,
            studyRecordQuery = EmptyStudyRecordQuery,
            studyPlanRepository = planRepository,
            businessDateProvider = FixedBusinessDateProvider
        )
        val useCase = SetWordAsMasteredUseCase(
            recordLearningEvent = RecordLearningEventUseCase(
                learningCommandPort = learningCommandPort,
                studyPlanRepository = planRepository,
                writeCoordinator = writeCoordinator,
                projectionCoordinator = projectionCoordinator,
                triggerSyncDrain = TriggerSyncDrainUseCase(syncRepository)
            ),
            getCurrentBusinessDateUseCase = GetCurrentBusinessDateUseCase(FixedBusinessDateProvider)
        )

        useCase(bookId = 10L, word = word, isNewWord = false)

        val command = checkNotNull(learningCommandPort.recordedCommand)
        assertEquals(10L, command.bookId)
        assertEquals(word, command.word)
        assertEquals(LearningEventAction.MASTERED, command.action)
        assertEquals(5, command.quality)
        assertFalse(checkNotNull(command.isNewWordOverride))
        assertEquals("2026-06-23", command.businessDate)
        assertEquals(15, command.dailyNewTarget)
        assertEquals(30, command.dailyReviewTarget)
        assertEquals("""{"isNewWord":false}""", command.payloadJson)
        assertEquals("2026-06-23", projectionStore.lastEvaluation?.businessDate)
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

    private class FakeProjectionQueue(
        private val commandPort: FakeLearningCommandPort
    ) : DailyStudyProjectionQueue {
        override suspend fun getById(clientEventId: String): DailyProgressProjectionTask {
            val command = checkNotNull(commandPort.recordedCommand)
            return DailyProgressProjectionTask(
                clientEventId = clientEventId,
                clientSequence = 1L,
                businessDate = command.businessDate,
                counts = DailyWordCounts(newCount = 0, reviewCount = 1),
                targets = DailyPlanTargets(
                    newTarget = checkNotNull(command.dailyNewTarget),
                    reviewTarget = checkNotNull(command.dailyReviewTarget)
                ),
                createdAtMs = 1L
            )
        }

        override suspend fun getPending(limit: Int): List<DailyProgressProjectionTask> = emptyList()
        override suspend fun delete(clientEventId: String) = Unit
    }

    private class FakeProjectionStore : DailyStudyProjectionStore {
        var lastEvaluation: DailyProgressEvaluation? = null

        override suspend fun apply(evaluation: DailyProgressEvaluation): DailyProgressTransition {
            lastEvaluation = evaluation
            return DailyProgressTransition.NotEligible(evaluation.businessDate)
        }
    }

    private class FakeStudyPlanRepository : StudyPlanRepository {
        override suspend fun saveStudyPlan(studyPlan: StudyPlan) = Unit
        override suspend fun getStudyPlan(): StudyPlan = StudyPlan(15, 30)
        override fun getStudyPlanFlow(): Flow<StudyPlan?> = flowOf(getPlan())
        override suspend fun saveStudyCount(dailyNewCount: Int, dailyReviewCount: Int) = Unit
        private fun getPlan() = StudyPlan(15, 30)
    }

    private object FixedBusinessDateProvider : BusinessDateProvider {
        override fun currentBusinessDate(): String = "2026-06-23"
    }

    private object EmptyStudyRecordQuery : StudyRecordQuery {
        override fun getStudyTotalDayCount(): Flow<Int> = emptyFlow()
        override fun getNewWordCount(date: String): Flow<Int> = emptyFlow()
        override fun getReviewWordCount(date: String): Flow<Int> = emptyFlow()
        override suspend fun getWordCounts(date: String): DailyWordCounts = DailyWordCounts(0, 0)
        override fun getDailyWordStats(startDate: String, endDate: String): Flow<List<DailyWordStats>> = emptyFlow()
        override fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecord>> = emptyFlow()
        override fun observeStudyDatesBetween(startDate: String, endDate: String): Flow<List<String>> = emptyFlow()
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
        override fun triggerDrain() { triggered = true }
        override fun scheduleBootstrapSync() = Unit
    }
}

private fun testWord(): Word = Word(
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
