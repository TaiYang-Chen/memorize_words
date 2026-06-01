package com.chen.memorizewords.feature.onboarding

import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingStep
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadCommandResult
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import com.chen.memorizewords.domain.wordbook.model.shop.ShopBooksQuery
import com.chen.memorizewords.domain.word.model.WordListRow
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import com.chen.memorizewords.domain.wordbook.repository.shop.RemoteWordBookRepository
import com.chen.memorizewords.domain.wordbook.service.onboarding.OnboardingCoordinator
import com.chen.memorizewords.domain.wordbook.service.WordBookShopFacade
import com.chen.memorizewords.domain.wordbook.usecase.onboarding.GetCurrentOnboardingSnapshotUseCase
import com.chen.memorizewords.domain.wordbook.usecase.onboarding.ObserveCurrentOnboardingSnapshotUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetStudyPlanFlowUseCase
import com.chen.memorizewords.domain.wordbook.usecase.SaveStudyPlanUseCase
import com.chen.memorizewords.domain.wordbook.usecase.SetCurrentWordBookUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `selectWordBook only updates pending selection`() = runTest {
        val viewModel = createViewModel()
        val book = testOnboardingWordBook(id = 21L)
        backgroundScope.launch { viewModel.step.collectLatest { } }

        advanceUntilIdle()
        viewModel.selectWordBook(book)
        advanceUntilIdle()

        assertEquals(book, viewModel.pendingSelectedWordBook.value)
        assertEquals(OnboardingStep.SELECT_WORD_BOOK, viewModel.step.value)
    }

    @Test
    fun `confirmSelectedWordBook advances to study plan`() = runTest {
        val viewModel = createViewModel()
        val book = testOnboardingWordBook(id = 33L)
        backgroundScope.launch { viewModel.step.collectLatest { } }
        backgroundScope.launch { viewModel.planUiState.collectLatest { } }

        advanceUntilIdle()
        viewModel.selectWordBook(book)
        viewModel.confirmSelectedWordBook()
        advanceUntilIdle()

        assertEquals(OnboardingStep.SET_STUDY_PLAN, viewModel.step.value)
        val state = viewModel.planUiState.value
        assertTrue(state is OnboardingPlanUiState.Content)
        assertEquals(book, (state as OnboardingPlanUiState.Content).wordBook)
    }

    private fun createViewModel(): OnboardingViewModel {
        val onboardingRepository = FakeOnboardingRepository()
        val studyPlanRepository = FakeStudyPlanRepository()
        val wordBookRepository = FakeWordBookRepository()
        val remoteWordBookRepository = FakeRemoteWordBookRepository()
        val coordinator = OnboardingCoordinator(
            onboardingRepository = onboardingRepository,
            wordBookShopFacade = WordBookShopFacade(remoteWordBookRepository),
            setCurrentWordBookUseCase = SetCurrentWordBookUseCase(wordBookRepository),
            saveStudyPlanUseCase = SaveStudyPlanUseCase(studyPlanRepository)
        )
        return OnboardingViewModel(
            getCurrentOnboardingSnapshotUseCase = GetCurrentOnboardingSnapshotUseCase(
                onboardingRepository
            ),
            observeCurrentOnboardingSnapshotUseCase = ObserveCurrentOnboardingSnapshotUseCase(
                onboardingRepository
            ),
            getStudyPlanFlowUseCase = GetStudyPlanFlowUseCase(studyPlanRepository),
            onboardingCoordinator = coordinator
        )
    }
}

private class FakeOnboardingRepository : OnboardingRepository {
    private val snapshotFlow = MutableStateFlow(OnboardingSnapshot())

    override fun getCurrentSnapshot(): OnboardingSnapshot = snapshotFlow.value

    override fun observeCurrentSnapshot(): Flow<OnboardingSnapshot> = snapshotFlow

    override suspend fun initializeSnapshotForUser(userId: Long, snapshot: OnboardingSnapshot?) {
        snapshotFlow.value = snapshot ?: OnboardingSnapshot()
    }

    override suspend fun replaceCurrentSnapshot(snapshot: OnboardingSnapshot?) {
        snapshotFlow.value = snapshot ?: OnboardingSnapshot()
    }

    override suspend fun completeOnboarding(selectedWordBookId: Long): OnboardingSnapshot {
        return OnboardingSnapshot(
            phase = com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase.COMPLETED,
            selectedWordBookId = selectedWordBookId
        ).also { snapshotFlow.value = it }
    }
}

private class FakeStudyPlanRepository : StudyPlanRepository {
    private val planFlow = MutableStateFlow(StudyPlan())

    override suspend fun saveStudyPlan(studyPlan: StudyPlan) {
        planFlow.value = studyPlan
    }

    override suspend fun getStudyPlan(): StudyPlan = planFlow.value

    override fun getStudyPlanFlow(): Flow<StudyPlan> = planFlow

    override suspend fun saveStudyCount(dailyNewCount: Int, dailyReviewCount: Int) {
        planFlow.value = planFlow.value.copy(
            dailyNewCount = dailyNewCount,
            dailyReviewCount = dailyReviewCount
        )
    }
}

private class FakeWordBookRepository : WordBookRepository {
    override fun getMyWordBooksMinimalFlow(): Flow<List<WordBookInfo>> = flowOf(emptyList())

    override fun getCurrentWordBookMinimalFlow(): Flow<WordBookInfo?> = flowOf(null)

    override suspend fun setCurrentWordBook(bookId: Long) = Unit

    override suspend fun getCurrentWordBook(): WordBook? = null

    override suspend fun getBookNameById(bookId: Long): String? = null

    override suspend fun getWordRowsPage(query: WordListQuery): PageSlice<WordListRow> {
        return PageSlice(items = emptyList(), hasNext = false)
    }

    override suspend fun getWordIdsPage(wordBookId: Long, pageIndex: Int, pageSize: Int): List<Long> {
        return emptyList()
    }

    override suspend fun getAllUnlearnedWordsForBook(bookId: Long): List<Word> = emptyList()

    override suspend fun updateBookStudyDay(bookId: Long, today: String) = Unit

    override suspend fun recordAnswerResult(bookId: Long, isCorrect: Boolean, today: String) = Unit
}

private class FakeRemoteWordBookRepository : RemoteWordBookRepository {
    override suspend fun getShopBooks(query: ShopBooksQuery): PageSlice<WordBook> {
        return PageSlice(items = emptyList(), hasNext = false)
    }

    override fun observeDownloadStates(): Flow<Map<Long, DownloadState>> = flowOf(emptyMap())

    override suspend fun downloadBook(
        book: WordBook,
        forceRefresh: Boolean,
        runInForeground: Boolean
    ): DownloadCommandResult {
        return DownloadCommandResult(message = "ok")
    }

    override suspend fun cancelDownload(bookId: Long) = Unit
}

private fun testOnboardingWordBook(id: Long): WordBook = WordBook(
    id = id,
    title = "考研词汇大纲",
    category = "大学",
    imgUrl = "",
    description = "适合备考使用",
    totalWords = 5500,
    isPublic = true,
    createdByUserId = null
)
