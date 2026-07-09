package com.chen.memorizewords.data.sync.bootstrap

import com.chen.memorizewords.core.common.calendar.CheckInBusinessCalendar
import com.chen.memorizewords.core.common.calendar.CheckInConfig
import com.chen.memorizewords.core.common.calendar.CheckInConfigDataSource
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.DailyStudyRecords
import com.chen.memorizewords.domain.study.repository.StudyDailyDurationSnapshot
import com.chen.memorizewords.domain.study.repository.StudySnapshotLocalStatePort
import com.chen.memorizewords.domain.sync.model.HomeStartupSnapshot
import com.chen.memorizewords.domain.sync.model.LoginBootstrap
import com.chen.memorizewords.domain.sync.model.LoginBootstrapCheckInStatus
import com.chen.memorizewords.domain.sync.model.LoginBootstrapDailyStudyDuration
import com.chen.memorizewords.domain.sync.model.LoginBootstrapStudyRecord
import com.chen.memorizewords.domain.sync.model.LoginBootstrapTodayStats
import com.chen.memorizewords.domain.sync.repository.HomeStartupSnapshotRepository
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress
import com.chen.memorizewords.domain.wordbook.repository.CurrentWordBookLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.WordBookLearningStateSnapshot
import com.chen.memorizewords.domain.wordbook.repository.WordBookSnapshotLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class LoginBootstrapApplierImplTest {

    @Test
    fun `missing bootstrap clears stale home snapshot`() = runBlocking {
        val snapshotRepository = FakeHomeStartupSnapshotRepository(
            initial = HomeStartupSnapshot(userId = 99L, businessDate = "2026-03-23")
        )
        val applier = createApplier(snapshotRepository = snapshotRepository)

        applier.apply(null)

        assertEquals(1, snapshotRepository.clearCalls)
        assertNull(snapshotRepository.getSnapshot())
    }

    @Test
    fun `unsupported bootstrap version clears stale home snapshot`() = runBlocking {
        val snapshotRepository = FakeHomeStartupSnapshotRepository(
            initial = HomeStartupSnapshot(userId = 99L, businessDate = "2026-03-23")
        )
        val applier = createApplier(snapshotRepository = snapshotRepository)

        applier.apply(LoginBootstrap(version = 2))

        assertEquals(1, snapshotRepository.clearCalls)
        assertNull(snapshotRepository.getSnapshot())
    }

    @Test
    fun `home snapshot ignores today stats from another business date`() = runBlocking {
        val snapshotRepository = FakeHomeStartupSnapshotRepository()
        val applier = createApplier(snapshotRepository = snapshotRepository)

        applier.apply(
            LoginBootstrap(
                businessDate = "2026-03-24",
                currentWordBook = WordBook(
                    id = 1001L,
                    title = "CET4",
                    category = "exam",
                    imgUrl = "",
                    description = "",
                    totalWords = 3000,
                    isSelected = true,
                    isPublic = true,
                    createdByUserId = null
                ),
                currentWordBookProgress = WordBookProgress(
                    wordBookId = 1001L,
                    wordBookName = "CET4",
                    learningCount = 120,
                    masteredCount = 40,
                    totalCount = 3000,
                    correctCount = 3,
                    wrongCount = 1,
                    studyDayCount = 5,
                    lastStudyDate = "2026-03-24"
                ),
                studyPlan = StudyPlan(dailyNewCount = 15, dailyReviewCount = 30),
                todayStats = LoginBootstrapTodayStats(
                    date = "2026-03-23",
                    newWordCount = 99,
                    reviewWordCount = 88,
                    studyDurationMs = 77L,
                    totalStudyDayCount = 66,
                    continuousCheckInDays = 55
                ),
                todayStudyRecords = listOf(
                    LoginBootstrapStudyRecord(
                        date = "2026-03-24",
                        wordId = 10L,
                        word = "apple",
                        definition = "a fruit",
                        isNewWord = true
                    ),
                    LoginBootstrapStudyRecord(
                        date = "2026-03-24",
                        wordId = 11L,
                        word = "review",
                        definition = "look again",
                        isNewWord = false
                    )
                ),
                todayStudyDuration = LoginBootstrapDailyStudyDuration(
                    date = "2026-03-24",
                    totalDurationMs = 12_000L,
                    updatedAtMs = 1770000000000L,
                    isNewPlanCompleted = false,
                    isReviewPlanCompleted = false
                ),
                checkInStatus = LoginBootstrapCheckInStatus(
                    continuousCheckInDays = 3,
                    lastCheckInDate = "2026-03-24",
                    makeupCardBalance = 1
                )
            )
        )

        val snapshot = snapshotRepository.getSnapshot()
        requireNotNull(snapshot)
        assertEquals("2026-03-24", snapshot.businessDate)
        assertEquals(1, snapshot.todayNewWordCount)
        assertEquals(1, snapshot.todayReviewWordCount)
        assertEquals(12_000L, snapshot.todayStudyDurationMs)
        assertEquals(3, snapshot.continuousCheckInDays)
        assertEquals(0, snapshot.totalStudyDayCount)
    }

    private fun createApplier(
        snapshotRepository: FakeHomeStartupSnapshotRepository
    ): LoginBootstrapApplierImpl {
        val checkInConfigDataSource = FakeCheckInConfigDataSource()
        return LoginBootstrapApplierImpl(
            onboardingRepository = FakeOnboardingRepository(),
            studyPlanLocalStatePort = FakeStudyPlanLocalStatePort(),
            currentWordBookLocalStatePort = FakeCurrentWordBookLocalStatePort(),
            wordBookSnapshotLocalStatePort = FakeWordBookSnapshotLocalStatePort(),
            studySnapshotLocalStatePort = FakeStudySnapshotLocalStatePort(),
            checkInConfigDataSource = checkInConfigDataSource,
            localAccountRepository = FakeLocalAccountRepository(),
            homeStartupSnapshotRepository = snapshotRepository,
            checkInBusinessCalendar = CheckInBusinessCalendar(checkInConfigDataSource)
        )
    }

    private class FakeHomeStartupSnapshotRepository(
        initial: HomeStartupSnapshot? = null
    ) : HomeStartupSnapshotRepository {
        private val flow = MutableStateFlow(initial)
        var clearCalls = 0

        override fun getSnapshot(): HomeStartupSnapshot? = flow.value

        override fun observeSnapshot(): Flow<HomeStartupSnapshot?> = flow

        override suspend fun saveSnapshot(snapshot: HomeStartupSnapshot) {
            flow.value = snapshot
        }

        override suspend fun clearSnapshot() {
            clearCalls++
            flow.value = null
        }
    }

    private class FakeCheckInConfigDataSource : CheckInConfigDataSource {
        private val flow = MutableStateFlow(CheckInConfig(timezoneId = "Asia/Shanghai"))

        override fun getConfig(): CheckInConfig = flow.value

        override fun getConfigFlow(): Flow<CheckInConfig> = flow

        override fun saveDayBoundaryOffsetMinutes(offsetMinutes: Int) {
            flow.value = flow.value.copy(dayBoundaryOffsetMinutes = offsetMinutes)
        }

        override fun saveTimezoneId(timezoneId: String) {
            flow.value = flow.value.copy(timezoneId = timezoneId)
        }

        override fun saveCachedMakeupCardBalance(balance: Int) {
            flow.value = flow.value.copy(cachedMakeupCardBalance = balance)
        }

        override fun consumeCachedMakeupCardBalance(count: Int) = Unit

        override fun saveLastCheckInSyncAt(timestamp: Long) {
            flow.value = flow.value.copy(lastCheckInSyncAt = timestamp)
        }

        override fun clearUserScopedState() {
            flow.value = CheckInConfig(timezoneId = "Asia/Shanghai")
        }
    }

    private class FakeLocalAccountRepository : LocalAccountRepository {
        override fun isLoggedIn(): Boolean = true

        override suspend fun getCurrentUser(): User? = null

        override suspend fun getCurrentUserId(): Long = 7L

        override fun getUserFlow(): Flow<User?> = flowOf(null)

        override suspend fun saveUser(user: User) = Unit

        override suspend fun clearUser() = Unit
    }

    private class FakeOnboardingRepository : OnboardingRepository {
        override fun getCurrentSnapshot(): OnboardingSnapshot = OnboardingSnapshot()

        override fun observeCurrentSnapshot(): Flow<OnboardingSnapshot> = flowOf(OnboardingSnapshot())

        override suspend fun initializeSnapshotForUser(userId: Long, snapshot: OnboardingSnapshot?) = Unit

        override suspend fun replaceCurrentSnapshot(snapshot: OnboardingSnapshot?) = Unit

        override suspend fun completeOnboarding(selectedWordBookId: Long): OnboardingSnapshot =
            OnboardingSnapshot(selectedWordBookId = selectedWordBookId)
    }

    private class FakeStudyPlanLocalStatePort : StudyPlanLocalStatePort {
        override fun overwriteFromRemote(studyPlan: StudyPlan?) = Unit

        override fun clearLocalState() = Unit
    }

    private class FakeCurrentWordBookLocalStatePort : CurrentWordBookLocalStatePort {
        override suspend fun getCurrentWordBookSelectionId(): Long? = null

        override suspend fun upsertBookAndSelectionFromRemote(book: WordBook?) = Unit

        override suspend fun upsertBooksAndSelectionFromRemote(books: List<WordBook>) = Unit

        override suspend fun clearSelectionFromRemote() = Unit

        override suspend fun clearLocalState() = Unit
    }

    private class FakeWordBookSnapshotLocalStatePort : WordBookSnapshotLocalStatePort {
        override suspend fun overwriteLearningStatesForBookFromRemote(
            bookId: Long,
            states: List<WordBookLearningStateSnapshot>
        ) = Unit

        override suspend fun overwriteProgressFromRemote(progress: List<WordBookProgress>) = Unit

        override suspend fun upsertProgressFromRemote(progress: List<WordBookProgress>) = Unit
    }

    private class FakeStudySnapshotLocalStatePort : StudySnapshotLocalStatePort {
        override suspend fun overwriteFavoritesFromRemote(favorites: List<WordFavorites>) = Unit

        override suspend fun overwriteStudyRecordsFromRemote(records: List<DailyStudyRecords>) = Unit

        override suspend fun upsertStudyRecordsFromRemote(records: List<DailyStudyRecords>) = Unit

        override suspend fun overwriteDailyDurationsFromRemote(durations: List<StudyDailyDurationSnapshot>) = Unit

        override suspend fun upsertDailyDurationsFromRemote(durations: List<StudyDailyDurationSnapshot>) = Unit

        override suspend fun overwriteCheckInRecordsFromRemote(records: List<CheckInRecord>) = Unit
    }
}
