package com.chen.memorizewords.domain.wordbook.service.onboarding
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingError
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
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
import com.chen.memorizewords.domain.wordbook.service.WordBookShopFacade
import com.chen.memorizewords.domain.wordbook.usecase.SaveStudyPlanUseCase
import com.chen.memorizewords.domain.wordbook.usecase.SetCurrentWordBookUseCase
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingCoordinatorTest {

    @Test
    fun `completeOnboarding downloads without foreground worker in onboarding`() = runBlocking {
        val operations = mutableListOf<String>()
        val remoteRepository = FakeRemoteWordBookRepository(operations = operations)
        val coordinator = createCoordinator(
            operations = operations,
            remoteWordBookRepository = remoteRepository
        )

        val result = coordinator.completeOnboarding(testWordBook(), StudyPlan())

        assertTrue(result.isSuccess)
        assertEquals(false, remoteRepository.lastRunInForeground)
    }

    @Test
    fun `completeOnboarding does not mark completed when save fails`() = runBlocking {
        val operations = mutableListOf<String>()
        val onboardingRepository = FakeOnboardingRepository(operations = operations)
        val studyPlanRepository = FakeStudyPlanRepository(
            operations = operations,
            saveError = RuntimeException("save failed")
        )
        val coordinator = createCoordinator(
            operations = operations,
            onboardingRepository = onboardingRepository,
            studyPlanRepository = studyPlanRepository
        )

        val result = coordinator.completeOnboarding(testWordBook(), StudyPlan())

        val error = result.exceptionOrNull() as? OnboardingOperationException
        assertEquals(OnboardingError.LocalPersistenceFailed, error?.onboardingError)
        assertEquals(0, onboardingRepository.completeOnboardingCalls)
        assertEquals(listOf("savePlan"), operations)
    }

    @Test
    fun `completeOnboarding returns required data unavailable when snapshot completion fails`() =
        runBlocking {
            val operations = mutableListOf<String>()
            val onboardingRepository = FakeOnboardingRepository(
                operations = operations,
                completeOnboardingError = IllegalStateException("selectedWordBookId missing")
            )
            val coordinator = createCoordinator(
                operations = operations,
                onboardingRepository = onboardingRepository
            )

            val result = coordinator.completeOnboarding(testWordBook(), StudyPlan())

            val error = result.exceptionOrNull() as? OnboardingOperationException
            assertEquals(OnboardingError.RequiredDataUnavailable, error?.onboardingError)
            assertEquals(1, onboardingRepository.completeOnboardingCalls)
            assertEquals(
                listOf("savePlan", "downloadBook", "setCurrentWordBook", "completeOnboarding"),
                operations
            )
        }

    @Test
    fun `completeOnboarding rejects reentry while mutation is running`() = runBlocking {
        val operations = mutableListOf<String>()
        val downloadStarted = CompletableDeferred<Unit>()
        val finishDownload = CompletableDeferred<Unit>()
        val remoteRepository = FakeRemoteWordBookRepository(
            operations = operations,
            onDownload = {
                downloadStarted.complete(Unit)
                finishDownload.await()
            }
        )
        val coordinator = createCoordinator(
            operations = operations,
            remoteWordBookRepository = remoteRepository
        )
        val book = testWordBook()

        val firstRequest = async { coordinator.completeOnboarding(book, StudyPlan()) }
        downloadStarted.await()

        val secondResult = coordinator.completeOnboarding(book, StudyPlan())
        val error = secondResult.exceptionOrNull() as? OnboardingOperationException
        assertEquals(OnboardingError.SyncDeferred, error?.onboardingError)

        finishDownload.complete(Unit)
        assertTrue(firstRequest.await().isSuccess)
    }

    @Test
    fun `completeOnboarding persists only after all local writes succeed`() = runBlocking {
        val operations = mutableListOf<String>()
        val onboardingRepository = FakeOnboardingRepository(operations = operations)
        val wordBookRepository = FakeWordBookRepository(operations = operations)
        val coordinator = createCoordinator(
            operations = operations,
            onboardingRepository = onboardingRepository,
            wordBookRepository = wordBookRepository
        )
        val book = testWordBook()

        val result = coordinator.completeOnboarding(book, StudyPlan())

        assertTrue(result.isSuccess)
        assertEquals(book.id, wordBookRepository.currentWordBookId)
        assertEquals(book.id, onboardingRepository.completedBookId)
        assertEquals(
            listOf("savePlan", "downloadBook", "setCurrentWordBook", "completeOnboarding"),
            operations
        )
    }

    @Test
    fun `completeOnboarding does not mark completed when selecting current word book fails`() =
        runBlocking {
            val operations = mutableListOf<String>()
            val onboardingRepository = FakeOnboardingRepository(operations = operations)
            val wordBookRepository = FakeWordBookRepository(
                operations = operations,
                setCurrentWordBookError = RuntimeException("set failed")
            )
            val coordinator = createCoordinator(
                operations = operations,
                onboardingRepository = onboardingRepository,
                wordBookRepository = wordBookRepository
            )

            val result = coordinator.completeOnboarding(testWordBook(), StudyPlan())

            val error = result.exceptionOrNull() as? OnboardingOperationException
            assertEquals(OnboardingError.LocalPersistenceFailed, error?.onboardingError)
            assertEquals(0, onboardingRepository.completeOnboardingCalls)
            assertEquals(listOf("savePlan", "downloadBook", "setCurrentWordBook"), operations)
        }

    private fun createCoordinator(
        operations: MutableList<String>,
        onboardingRepository: FakeOnboardingRepository = FakeOnboardingRepository(operations),
        remoteWordBookRepository: FakeRemoteWordBookRepository = FakeRemoteWordBookRepository(operations),
        wordBookRepository: FakeWordBookRepository = FakeWordBookRepository(operations),
        studyPlanRepository: FakeStudyPlanRepository = FakeStudyPlanRepository(operations)
    ): OnboardingCoordinator {
        return OnboardingCoordinator(
            onboardingRepository = onboardingRepository,
            wordBookShopFacade = WordBookShopFacade(remoteWordBookRepository),
            setCurrentWordBookUseCase = SetCurrentWordBookUseCase(wordBookRepository),
            saveStudyPlanUseCase = SaveStudyPlanUseCase(studyPlanRepository)
        )
    }

    private fun testWordBook(): WordBook {
        return WordBook(
            id = 7L,
            title = "CET-4",
            category = "澶у",
            imgUrl = "",
            description = "test",
            totalWords = 1000,
            isPublic = true,
            createdByUserId = null
        )
    }
}

private class FakeOnboardingRepository(
    private val operations: MutableList<String>,
    private val completeOnboardingError: Throwable? = null
) : OnboardingRepository {
    var completeOnboardingCalls: Int = 0
    var completedBookId: Long? = null

    override fun getCurrentSnapshot(): OnboardingSnapshot = OnboardingSnapshot()

    override fun observeCurrentSnapshot(): Flow<OnboardingSnapshot> = flowOf(OnboardingSnapshot())

    override suspend fun initializeSnapshotForUser(userId: Long, snapshot: OnboardingSnapshot?) = Unit

    override suspend fun replaceCurrentSnapshot(snapshot: OnboardingSnapshot?) = Unit

    override suspend fun completeOnboarding(selectedWordBookId: Long): OnboardingSnapshot {
        completeOnboardingCalls += 1
        completedBookId = selectedWordBookId
        operations += "completeOnboarding"
        completeOnboardingError?.let { throw it }
        return OnboardingSnapshot(
            phase = OnboardingPhase.COMPLETED,
            selectedWordBookId = selectedWordBookId
        )
    }
}

private class FakeRemoteWordBookRepository(
    private val operations: MutableList<String>,
    private val onDownload: suspend () -> Unit = {}
) : RemoteWordBookRepository {
    var lastRunInForeground: Boolean? = null

    override suspend fun getShopBooks(query: ShopBooksQuery): PageSlice<WordBook> {
        return PageSlice(emptyList(), hasNext = false)
    }

    override fun observeDownloadStates(): Flow<Map<Long, DownloadState>> = flowOf(emptyMap())

    override suspend fun downloadBook(
        book: WordBook,
        forceRefresh: Boolean,
        runInForeground: Boolean
    ): DownloadCommandResult {
        lastRunInForeground = runInForeground
        operations += "downloadBook"
        onDownload()
        return DownloadCommandResult("ok")
    }

    override suspend fun cancelDownload(bookId: Long) = Unit
}

private class FakeWordBookRepository(
    private val operations: MutableList<String>,
    private val setCurrentWordBookError: Throwable? = null
) : WordBookRepository {
    var currentWordBookId: Long? = null

    override fun getMyWordBooksMinimalFlow(): Flow<List<WordBookInfo>> = flowOf(emptyList())

    override fun getCurrentWordBookMinimalFlow(): Flow<WordBookInfo?> = flowOf(null)

    override suspend fun setCurrentWordBook(bookId: Long) {
        operations += "setCurrentWordBook"
        setCurrentWordBookError?.let { throw it }
        currentWordBookId = bookId
    }

    override suspend fun getCurrentWordBook(): WordBook? = null

    override suspend fun getBookNameById(bookId: Long): String? = null

    override suspend fun getWordRowsPage(query: WordListQuery): PageSlice<WordListRow> {
        return PageSlice(emptyList(), hasNext = false)
    }

    override suspend fun getWordIdsPage(
        wordBookId: Long,
        pageIndex: Int,
        pageSize: Int
    ): List<Long> = emptyList()

    override suspend fun getAllUnlearnedWordsForBook(bookId: Long): List<Word> = emptyList()

    override suspend fun updateBookStudyDay(bookId: Long, today: String) = Unit

    override suspend fun recordAnswerResult(bookId: Long, isCorrect: Boolean, today: String) = Unit
}

private class FakeStudyPlanRepository(
    private val operations: MutableList<String>,
    private val saveError: Throwable? = null
) : StudyPlanRepository {
    val saveCalls = AtomicInteger(0)

    override suspend fun saveStudyPlan(studyPlan: StudyPlan) {
        saveCalls.incrementAndGet()
        operations += "savePlan"
        saveError?.let { throw it }
    }

    override suspend fun getStudyPlan(): StudyPlan = StudyPlan()

    override fun getStudyPlanFlow(): Flow<StudyPlan> = emptyFlow()

    override suspend fun saveStudyCount(dailyNewCount: Int, dailyReviewCount: Int) = Unit
}
