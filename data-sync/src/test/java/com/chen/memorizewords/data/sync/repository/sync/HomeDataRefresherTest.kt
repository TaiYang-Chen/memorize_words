package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.core.network.http.PageData
import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.CheckInConfigDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.CheckInRecordDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.CheckInStatusDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.DailyStudyDurationDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.FavoriteDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.PendingWordBookUpdateDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.StudyRecordDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.WordBookProgressDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.WordBookUpdateActionRequest
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.WordBookUpdateCandidateDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.WordBookUpdateManifestDto
import com.chen.memorizewords.data.sync.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.data.sync.remoteapi.dto.wordbook.WordDto
import com.chen.memorizewords.data.sync.remoteapi.dto.wordstate.WordStateDto
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.DailyStudyRecords
import com.chen.memorizewords.domain.study.repository.StudyDailyDurationSnapshot
import com.chen.memorizewords.domain.study.repository.StudySnapshotLocalStatePort
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadCommandResult
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import com.chen.memorizewords.domain.wordbook.model.shop.ShopBooksQuery
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress
import com.chen.memorizewords.domain.wordbook.repository.CurrentWordBookLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.WordBookLearningStateSnapshot
import com.chen.memorizewords.domain.wordbook.repository.WordBookSnapshotLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.shop.RemoteWordBookRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class HomeDataRefresherTest {

    @Test
    fun `refresh persists selected book word states before returning`() = runBlocking {
        val selectedBookId = 42L
        val remote = FakeRemoteUserSyncDataSource(
            selectedBookId = selectedBookId,
            wordStates = List(6) { index ->
                WordStateDto(
                    wordId = index + 1L,
                    bookId = selectedBookId,
                    totalLearnCount = 1,
                    lastLearnTime = 100L + index,
                    nextReviewTime = 200L + index,
                    masteryLevel = 5,
                    userStatus = 1,
                    repetition = 1,
                    interval = 1L,
                    efactor = 2.5
                )
            }
        )
        val studyStatePort = RecordingStudySnapshotLocalStatePort()
        val wordBookStatePort = RecordingWordBookSnapshotLocalStatePort()
        val remoteWordBookRepository = RecordingRemoteWordBookRepository()
        val refresher = HomeDataRefresher(
            remoteUserSyncDataSource = remote,
            studyPlanLocalStatePort = RecordingStudyPlanLocalStatePort(),
            currentWordBookLocalStatePort = RecordingCurrentWordBookLocalStatePort(),
            wordBookSnapshotLocalStatePort = wordBookStatePort,
            studySnapshotLocalStatePort = studyStatePort,
            remoteWordBookRepository = remoteWordBookRepository
        )

        val result = refresher.refresh()

        assertTrue(result.isSuccess)
        assertEquals(selectedBookId, remote.requestedWordStateBookId)
        assertEquals(selectedBookId, remoteWordBookRepository.downloadedBookId)
        assertEquals(6, studyStatePort.learningStates.count { it.userStatus == 1 })
        assertEquals(6, wordBookStatePort.learningStates.count { it.userStatus == 1 })
    }
}

private class FakeRemoteUserSyncDataSource(
    private val selectedBookId: Long,
    private val wordStates: List<WordStateDto>
) : RemoteUserSyncDataSource {
    var requestedWordStateBookId: Long? = null

    override suspend fun getOnboardingState(): Result<OnboardingSnapshot?> = Result.success(null)

    override suspend fun updateOnboardingState(snapshot: OnboardingSnapshot): Result<Unit> =
        Result.success(Unit)

    override suspend fun getStudyPlan(): Result<StudyPlan?> = Result.success(StudyPlan())

    override suspend fun updateStudyPlan(studyPlan: StudyPlan): Result<Unit> = Result.success(Unit)

    override suspend fun getMyWordBooks(): Result<List<WordBookDto>> {
        return Result.success(
            listOf(
                WordBookDto(
                    id = selectedBookId,
                    title = "Book",
                    category = "Category",
                    imgUrl = "",
                    description = "",
                    totalWords = 100,
                    learnedWords = 0,
                    contentVersion = 1L,
                    isNew = false,
                    isHot = false,
                    isSelected = true,
                    isPublic = true,
                    createdByUserId = null
                )
            )
        )
    }

    override suspend fun addMyWordBook(bookId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun getWordStates(
        bookId: Long,
        page: Int,
        count: Int
    ): Result<PageData<WordStateDto>> {
        requestedWordStateBookId = bookId
        val items = if (page == 0) wordStates else emptyList()
        return Result.success(PageData(items = items, page = page, size = count, total = wordStates.size.toLong()))
    }

    override suspend fun addFavorite(favorite: WordFavorites): Result<Unit> = Result.success(Unit)

    override suspend fun getFavorites(page: Int, count: Int): Result<PageData<FavoriteDto>> =
        Result.success(PageData(emptyList(), page, count, 0))

    override suspend fun removeFavorite(wordId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun upsertWordState(
        bookId: Long,
        wordId: Long,
        totalLearnCount: Int,
        lastLearnTime: Long,
        nextReviewTime: Long,
        masteryLevel: Int,
        userStatus: Int,
        repetition: Int,
        interval: Long,
        efactor: Double
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteWordStatesByBookId(bookId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun upsertWordBookProgress(
        bookId: Long,
        bookName: String,
        learnedCount: Int,
        masteredCount: Int,
        totalCount: Int,
        correctCount: Int,
        wrongCount: Int,
        studyDayCount: Int,
        lastStudyDate: String
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getWordBookProgressList(): Result<List<WordBookProgressDto>> =
        Result.success(emptyList())

    override suspend fun getCurrentWordBookUpdateCandidate(
        trigger: String
    ): Result<WordBookUpdateCandidateDto?> = Result.success(null)

    override suspend fun reportCurrentWordBookUpdateAction(
        request: WordBookUpdateActionRequest
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getCurrentWordBookUpdateManifest(
        version: Long
    ): Result<WordBookUpdateManifestDto> = error("Unused")

    override suspend fun getCurrentWordBookUpdateWords(
        version: Long,
        page: Int,
        count: Int
    ): Result<PageData<WordDto>> = error("Unused")

    override suspend fun completeCurrentWordBookUpdate(version: Long): Result<Unit> =
        Result.success(Unit)

    override suspend fun getPendingWordBookUpdate(bookId: Long): Result<PendingWordBookUpdateDto?> =
        Result.success(null)

    override suspend fun ignoreWordBookUpdate(bookId: Long, version: Long): Result<Unit> =
        Result.success(Unit)

    override suspend fun getWordBookUpdateManifest(
        bookId: Long,
        version: Long
    ): Result<WordBookUpdateManifestDto> = error("Unused")

    override suspend fun getWordBookUpdateWords(
        bookId: Long,
        version: Long,
        page: Int,
        count: Int
    ): Result<PageData<WordDto>> = error("Unused")

    override suspend fun completeWordBookUpdate(bookId: Long, version: Long): Result<Unit> =
        Result.success(Unit)

    override suspend fun appendStudyRecord(
        date: String,
        wordId: Long,
        word: String,
        definition: String,
        isNewWord: Boolean
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getStudyRecords(page: Int, count: Int): Result<PageData<StudyRecordDto>> =
        Result.success(PageData(emptyList(), page, count, 0))

    override suspend fun getDailyStudyDurations(
        page: Int,
        count: Int
    ): Result<PageData<DailyStudyDurationDto>> =
        Result.success(PageData(emptyList(), page, count, 0))

    override suspend fun upsertDailyStudyDuration(
        date: String,
        totalDurationMs: Long,
        updatedAt: Long,
        isNewPlanCompleted: Boolean,
        isReviewPlanCompleted: Boolean
    ): Result<Unit> = Result.success(Unit)

    override suspend fun setCurrentWordBookSelection(bookId: Long): Result<Unit> =
        Result.success(Unit)

    override suspend fun getCheckInConfig(): Result<CheckInConfigDto?> = Result.success(null)

    override suspend fun updateCheckInConfig(
        dayBoundaryOffsetMinutes: Int,
        timezoneId: String
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getCheckInStatus(): Result<CheckInStatusDto?> = Result.success(null)

    override suspend fun getCheckInRecords(page: Int, count: Int): Result<PageData<CheckInRecordDto>> =
        Result.success(PageData(emptyList(), page, count, 0))

    override suspend fun upsertCheckInRecord(
        date: String,
        type: String,
        signedAt: Long,
        updatedAt: Long
    ): Result<Unit> = Result.success(Unit)
}

private class RecordingStudySnapshotLocalStatePort : StudySnapshotLocalStatePort {
    var learningStates: List<WordLearningState> = emptyList()

    override suspend fun overwriteFavoritesFromRemote(favorites: List<WordFavorites>) = Unit

    override suspend fun overwriteLearningStatesForBookFromRemote(
        bookId: Long,
        states: List<WordLearningState>
    ) {
        learningStates = states
    }

    override suspend fun overwriteStudyRecordsFromRemote(records: List<DailyStudyRecords>) = Unit

    override suspend fun overwriteDailyDurationsFromRemote(
        durations: List<StudyDailyDurationSnapshot>
    ) = Unit

    override suspend fun overwriteCheckInRecordsFromRemote(records: List<CheckInRecord>) = Unit
}

private class RecordingWordBookSnapshotLocalStatePort : WordBookSnapshotLocalStatePort {
    var learningStates: List<WordBookLearningStateSnapshot> = emptyList()

    override suspend fun overwriteLearningStatesForBookFromRemote(
        bookId: Long,
        states: List<WordBookLearningStateSnapshot>
    ) {
        learningStates = states
    }

    override suspend fun overwriteProgressFromRemote(progress: List<WordBookProgress>) = Unit
}

private class RecordingStudyPlanLocalStatePort : StudyPlanLocalStatePort {
    override fun overwriteFromRemote(studyPlan: StudyPlan?) = Unit

    override fun clearLocalState() = Unit
}

private class RecordingCurrentWordBookLocalStatePort : CurrentWordBookLocalStatePort {
    override suspend fun overwriteFromRemote(bookId: Long?) = Unit

    override suspend fun clearLocalState() = Unit
}

private class RecordingRemoteWordBookRepository : RemoteWordBookRepository {
    var downloadedBookId: Long? = null

    override suspend fun getShopBooks(query: ShopBooksQuery): PageSlice<WordBook> =
        PageSlice(emptyList(), hasNext = false)

    override suspend fun getShopBookById(bookId: Long): WordBook? = null

    override fun observeDownloadStates(): Flow<Map<Long, DownloadState>> = flowOf(emptyMap())

    override suspend fun downloadBook(
        book: WordBook,
        forceRefresh: Boolean,
        runInForeground: Boolean
    ): DownloadCommandResult {
        downloadedBookId = book.id
        return DownloadCommandResult("Download queued")
    }

    override suspend fun cancelDownload(bookId: Long) = Unit
}
