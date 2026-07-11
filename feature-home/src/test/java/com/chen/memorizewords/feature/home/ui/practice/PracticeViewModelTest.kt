package com.chen.memorizewords.feature.home.ui.practice

import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.account.model.membership.MembershipCheckInReward
import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.model.membership.MembershipFeatureAccess
import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import com.chen.memorizewords.domain.account.policy.MembershipEntitlementPolicy
import com.chen.memorizewords.domain.account.repository.membership.MembershipRepository
import com.chen.memorizewords.domain.account.usecase.membership.ObserveMembershipStatusUseCase
import com.chen.memorizewords.domain.account.usecase.membership.ResolveMembershipFeatureAccessUseCase
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.repository.FloatingWordDisplayRecordRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import com.chen.memorizewords.domain.floating.service.FloatingReviewFacade
import com.chen.memorizewords.domain.practice.PracticeAvailability
import com.chen.memorizewords.domain.practice.PracticeDailyDurationStats
import com.chen.memorizewords.domain.practice.PracticeRecordRepository
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.PracticeSessionRecordRepository
import com.chen.memorizewords.domain.practice.PracticeSettings
import com.chen.memorizewords.domain.practice.PracticeSettingsRepository
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.practice.service.PracticeFacade
import com.chen.memorizewords.domain.practice.usage.EvaluationUsage
import com.chen.memorizewords.domain.practice.usage.ObservePracticeUsageUseCase
import com.chen.memorizewords.domain.practice.usage.PracticeUsage
import com.chen.memorizewords.domain.practice.usage.PracticeUsageRepository
import com.chen.memorizewords.domain.practice.usage.PracticeUsageState
import com.chen.memorizewords.domain.practice.usage.RefreshPracticeUsageUseCase
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.WordLearningRepository
import com.chen.memorizewords.domain.word.model.WordListRow
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.domain.word.model.word.WordForm
import com.chen.memorizewords.domain.word.model.word.WordQuickLookupResult
import com.chen.memorizewords.domain.word.model.word.WordRoot
import com.chen.memorizewords.domain.word.repository.WordRepository
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookContentState
import com.chen.memorizewords.domain.wordbook.model.WordBookContentStatus
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {

    @Test
    fun `enabling floating review enables app launch auto start and dispatches start`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val floatingSettings = FakeFloatingWordSettingsRepository(
                FloatingWordSettings(enabled = false, autoStartOnAppLaunch = false)
            )
            val viewModel = createViewModel(
                floatingSettingsRepository = floatingSettings,
                membershipRepository = FakeMembershipRepository(active = true)
            )
            val routes = collectRoutes(viewModel)

            viewModel.onFloatingEnabledChanged(true)
            advanceUntilIdle()

            assertTrue(floatingSettings.current.enabled)
            assertTrue(floatingSettings.current.autoStartOnAppLaunch)
            assertEquals(1, floatingSettings.saveCount)
            val route = assertIs<PracticeViewModel.Route.DispatchFloatingAction>(routes.single())
            assertEquals(FloatingWordActions.ACTION_START, route.action)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `disabling floating review disables app launch auto start and dispatches stop`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val floatingSettings = FakeFloatingWordSettingsRepository(
                FloatingWordSettings(enabled = true, autoStartOnAppLaunch = true)
            )
            val viewModel = createViewModel(
                floatingSettingsRepository = floatingSettings,
                membershipRepository = FakeMembershipRepository(active = true)
            )
            val routes = collectRoutes(viewModel)

            viewModel.onFloatingEnabledChanged(false)
            advanceUntilIdle()

            assertFalse(floatingSettings.current.enabled)
            assertFalse(floatingSettings.current.autoStartOnAppLaunch)
            assertEquals(1, floatingSettings.saveCount)
            val route = assertIs<PracticeViewModel.Route.DispatchFloatingAction>(routes.single())
            assertEquals(FloatingWordActions.ACTION_STOP, route.action)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `unknown membership status does not disable and upload floating settings`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val floatingSettings = FakeFloatingWordSettingsRepository(
                FloatingWordSettings(enabled = true, autoStartOnAppLaunch = true)
            )
            val membershipRepository = FakeMembershipRepository(initialStatus = null)

            createViewModel(
                floatingSettingsRepository = floatingSettings,
                membershipRepository = membershipRepository
            )
            advanceUntilIdle()

            assertTrue(floatingSettings.current.enabled)
            assertTrue(floatingSettings.current.autoStartOnAppLaunch)
            assertEquals(0, floatingSettings.saveCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `inactive membership status disables floating settings and dispatches stop`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val floatingSettings = FakeFloatingWordSettingsRepository(
                FloatingWordSettings(enabled = true, autoStartOnAppLaunch = true)
            )
            val membershipRepository = FakeMembershipRepository(initialStatus = null)
            val viewModel = createViewModel(
                floatingSettingsRepository = floatingSettings,
                membershipRepository = membershipRepository
            )
            val routes = collectRoutes(viewModel)
            advanceUntilIdle()

            membershipRepository.emit(MembershipStatus(active = false))
            advanceUntilIdle()

            assertFalse(floatingSettings.current.enabled)
            assertFalse(floatingSettings.current.autoStartOnAppLaunch)
            assertEquals(1, floatingSettings.saveCount)
            val route = assertIs<PracticeViewModel.Route.DispatchFloatingAction>(routes.single())
            assertEquals(FloatingWordActions.ACTION_STOP, route.action)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun TestScope.collectRoutes(
        viewModel: PracticeViewModel
    ): MutableList<Any> {
        val routes = mutableListOf<Any>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvent.collect { event ->
                if (event is UiEvent.Navigation.Route) {
                    routes += event.target
                }
            }
        }
        return routes
    }

    private fun createViewModel(
        floatingSettingsRepository: FakeFloatingWordSettingsRepository,
        membershipRepository: FakeMembershipRepository
    ): PracticeViewModel {
        val resourceProvider = FakeResourceProvider()
        val practiceUsageRepository = FakePracticeUsageRepository()
        return PracticeViewModel(
            practiceFacade = PracticeFacade(
                practiceWordProvider = FakePracticeWordProvider(),
                practiceSettingsRepository = FakePracticeSettingsRepository(),
                practiceRecordRepository = FakePracticeRecordRepository(),
                practiceSessionRecordRepository = FakePracticeSessionRecordRepository()
            ),
            resourceProvider = resourceProvider,
            practiceUiMapper = PracticeUiMapper(resourceProvider),
            floatingReviewFacade = FloatingReviewFacade(
                floatingWordSettingsRepository = floatingSettingsRepository,
                floatingWordDisplayRecordRepository = FakeFloatingWordDisplayRecordRepository(),
                wordLearningRepository = FakeWordLearningRepository(),
                wordRepository = FakeWordRepository(),
                wordBookRepository = FakeWordBookRepository()
            ),
            observeMembershipStatusUseCase = ObserveMembershipStatusUseCase(membershipRepository),
            resolveMembershipFeatureAccessUseCase = ResolveMembershipFeatureAccessUseCase(
                repository = membershipRepository,
                policy = MembershipEntitlementPolicy()
            ),
            observePracticeUsageUseCase = ObservePracticeUsageUseCase(practiceUsageRepository),
            refreshPracticeUsageUseCase = RefreshPracticeUsageUseCase(practiceUsageRepository)
        )
    }

    private class FakePracticeUsageRepository : PracticeUsageRepository {
        override fun observe(): Flow<PracticeUsageState> = flowOf(PracticeUsageState.Unknown)

        override suspend fun refresh(): Result<PracticeUsage> =
            Result.failure(IllegalStateException("Practice usage is not needed by this test"))

        override suspend fun updateEvaluationUsage(usage: EvaluationUsage) = Unit

        override suspend fun clear() = Unit
    }

    private class FakeFloatingWordSettingsRepository(
        initial: FloatingWordSettings
    ) : FloatingWordSettingsRepository {
        private val state = MutableStateFlow(initial)
        var saveCount = 0
            private set
        val current: FloatingWordSettings
            get() = state.value

        override fun observeSettings(): Flow<FloatingWordSettings> = state

        override suspend fun getSettings(): FloatingWordSettings = state.value

        override suspend fun saveSettings(settings: FloatingWordSettings) {
            saveCount += 1
            state.value = settings
        }

        override suspend fun updateBallPosition(x: Int, y: Int, dockState: FloatingDockState?) {
            state.value = state.value.copy(floatingBallX = x, floatingBallY = y, dockState = dockState)
        }
    }

    private class FakeMembershipRepository(
        initialStatus: MembershipStatus? = null,
        active: Boolean? = null
    ) : MembershipRepository {
        private val status = MutableStateFlow(active?.let(::membershipStatus) ?: initialStatus)

        override fun observeStatus(): Flow<MembershipStatus?> = status

        override suspend fun getCachedStatus(): MembershipStatus? = status.value

        override suspend fun refreshStatus(): Result<MembershipStatus> =
            Result.success(status.value ?: MembershipStatus(active = false))

        override suspend fun checkIn(): Result<MembershipCheckInReward> =
            Result.success(
                MembershipCheckInReward(
                    granted = true,
                    grantDays = 1,
                    rewardDate = "2026-07-03",
                    membership = membershipStatus(active = true)
                )
            )

        fun emit(value: MembershipStatus?) {
            status.value = value
        }

        private fun membershipStatus(active: Boolean): MembershipStatus {
            return if (active) {
                MembershipStatus(
                    active = true,
                    validUntilDate = "2099-12-31",
                    validUntilAtMs = 4_102_444_740_000L
                )
            } else {
                MembershipStatus(active = false)
            }
        }
    }

    private class FakeResourceProvider : ResourceProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String =
            if (formatArgs.isEmpty()) resId.toString() else "$resId:${formatArgs.joinToString()}"
    }

    private class FakePracticeWordProvider : PracticeWordProvider {
        override suspend fun loadWords(selectedIds: LongArray?, randomCount: Int, defaultLimit: Int): List<Word> = emptyList()
        override suspend fun getPracticeAvailability(): PracticeAvailability = PracticeAvailability.AVAILABLE
        override suspend fun resolveBookId(): Long? = null
        override suspend fun loadReviewWordsForPicker(): List<Word> = emptyList()
    }

    private class FakePracticeSettingsRepository : PracticeSettingsRepository {
        override fun observeSettings(): Flow<PracticeSettings> = flowOf(PracticeSettings())
        override suspend fun getSettings(): PracticeSettings = PracticeSettings()
        override suspend fun saveSettings(settings: PracticeSettings) = Unit
    }

    private class FakePracticeRecordRepository : PracticeRecordRepository {
        override suspend fun addPracticeDuration(durationMs: Long) = Unit
        override fun getTodayPracticeDurationMs(): Flow<Long> = flowOf(0L)
        override fun getPracticeTotalDurationMs(): Flow<Long> = flowOf(0L)
        override fun getContinuousPracticeDays(): Flow<Int> = flowOf(0)
        override fun getRecentPracticeDurationStats(dayCount: Int): Flow<List<PracticeDailyDurationStats>> = flowOf(emptyList())
    }

    private class FakePracticeSessionRecordRepository : PracticeSessionRecordRepository {
        override suspend fun saveSessionRecord(record: PracticeSessionRecord) = Unit
        override fun getRecentSessionRecords(dayCount: Int): Flow<List<PracticeSessionRecord>> = flowOf(emptyList())
        override suspend fun getSessionRecord(recordId: Long): PracticeSessionRecord? = null
    }

    private class FakeFloatingWordDisplayRecordRepository : FloatingWordDisplayRecordRepository {
        override suspend fun recordDisplay(wordId: Long) = Unit
        override suspend fun getRecordByDate(date: String): FloatingWordDisplayRecord? = null
    }

    private class FakeWordLearningRepository : WordLearningRepository {
        override suspend fun getLearningStatesByIds(wordBookId: Long, ids: List<Long>): Map<Long, WordLearningState> = emptyMap()
        override suspend fun getLearningStatesByBookId(bookId: Long): List<WordLearningState> = emptyList()
        override suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long> = emptyList()
    }

    private class FakeWordRepository : WordRepository {
        override suspend fun getWordsByIds(ids: List<Long>): List<Word> = emptyList()
        override suspend fun getWordById(wordId: Long): Word? = null
        override suspend fun getWordForms(wordId: Long): List<WordForm> = emptyList()
        override suspend fun getRootWordByWordId(wordId: Long): List<WordRoot> = emptyList()
        override suspend fun getWordExamples(wordId: Long): List<WordExample> = emptyList()
        override suspend fun getWordDefinitions(wordId: Long): List<WordDefinitions> = emptyList()
        override suspend fun getRandomDefinition(wordId: Long): WordDefinitions = error("Not used")
        override suspend fun getRandomDefinitionsByPos(wordId: Long, limit: Int): List<WordDefinitions> = emptyList()
        override suspend fun getWordByWordString(word: String): Word? = null
        override suspend fun lookupWordQuick(normalizedWord: String, rawWord: String): WordQuickLookupResult = error("Not used")
    }

    private class FakeWordBookRepository : WordBookRepository {
        private val currentBookId = 1L

        override fun getMyWordBooksMinimalFlow(): Flow<List<WordBookInfo>> = flowOf(emptyList())
        override fun observeCurrentWordBookSelectionId(): Flow<Long?> = flowOf(currentBookId)
        override fun getCurrentWordBookMinimalFlow(): Flow<WordBookInfo?> = flowOf(null)
        override fun observeWordBookContentState(bookId: Long): Flow<WordBookContentState?> =
            flowOf(contentState(bookId))
        override suspend fun setCurrentWordBook(bookId: Long) = Unit
        override suspend fun deleteMyWordBook(bookId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun createMyWordBook(
            title: String,
            category: String,
            description: String,
            words: List<String>
        ): Result<WordBookInfo> = Result.failure(UnsupportedOperationException("Not used"))
        override suspend fun getCurrentWordBookSelectionId(): Long? = currentBookId
        override suspend fun getCurrentWordBook(): WordBook? = null
        override suspend fun getWordBookContentState(bookId: Long): WordBookContentState? =
            contentState(bookId)
        override suspend fun getBookNameById(bookId: Long): String? = null
        override suspend fun getWordListSummary(
            wordBookId: Long,
            now: Long
        ): com.chen.memorizewords.domain.wordbook.model.WordListSummary = com.chen.memorizewords.domain.wordbook.model.WordListSummary()
        override suspend fun getWordRowsPage(query: WordListQuery): PageSlice<WordListRow> = PageSlice(emptyList(), false)
        override suspend fun getWordRowIds(query: WordListQuery, limit: Int): List<Long> = emptyList()
        override suspend fun getWordIdsPage(wordBookId: Long, pageIndex: Int, pageSize: Int): List<Long> = emptyList()
        override suspend fun getAllUnlearnedWordsForBook(bookId: Long): List<Word> = emptyList()
        override suspend fun getUnlearnedWordIdsForBook(
            bookId: Long,
            count: Int,
            orderType: WordOrderType,
            excludeIds: Set<Long>
        ): List<Long> = emptyList()

        private fun contentState(bookId: Long): WordBookContentState =
            WordBookContentState(
                bookId = bookId,
                targetVersion = 1L,
                localVersion = 1L,
                status = WordBookContentStatus.READY,
                downloadedWords = 0,
                totalWords = 0,
                lastError = null
            )
    }
}
