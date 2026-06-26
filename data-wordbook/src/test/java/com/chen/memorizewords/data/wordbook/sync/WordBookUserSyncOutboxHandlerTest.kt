package com.chen.memorizewords.data.wordbook.sync

import com.chen.memorizewords.core.common.calendar.CheckInConfig
import com.chen.memorizewords.core.common.calendar.CheckInConfigDataSource
import com.chen.memorizewords.core.network.http.PageData
import com.chen.memorizewords.core.network.remote.HttpStatusException
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.CheckInConfigDto
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.CheckInRecordDto
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.CheckInStatusDto
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.DailyStudyDurationDto
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.FavoriteDto
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.PendingWordBookUpdateDto
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.StudyRecordDto
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.WordBookProgressDto
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.WordBookUpdateActionRequest
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.WordBookUpdateCandidateDto
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.WordBookUpdateManifestDto
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordDto
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordstate.WordStateDto
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.sync.OutboxRecord
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.WordBookDeleteSyncPayload
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class WordBookUserSyncOutboxHandlerTest {

    @Test
    fun `word book delete succeeds when remote delete succeeds`() = runBlocking {
        val remote = FakeRemoteUserSyncDataSource(Result.success(Unit))
        val handler = handler(remote)

        handler.handle(wordBookDeleteRecord(bookId = 7L))

        assertEquals(listOf(7L), remote.removedBookIds)
    }

    @Test
    fun `word book delete treats missing remote book as success`() = runBlocking {
        val remote = FakeRemoteUserSyncDataSource(
            Result.failure(HttpStatusException(400, "bookId invalid"))
        )
        val handler = handler(remote)

        handler.handle(wordBookDeleteRecord(bookId = 7L))

        assertEquals(listOf(7L), remote.removedBookIds)
    }

    @Test
    fun `word book delete keeps unexpected client errors visible to sync policy`() = runBlocking {
        val remote = FakeRemoteUserSyncDataSource(
            Result.failure(HttpStatusException(400, "bad request"))
        )
        val handler = handler(remote)

        assertFailsWith<HttpStatusException> {
            handler.handle(wordBookDeleteRecord(bookId = 7L))
        }
        assertEquals(listOf(7L), remote.removedBookIds)
    }

    private fun handler(remote: RemoteUserSyncDataSource): WordBookUserSyncOutboxHandler {
        return WordBookUserSyncOutboxHandler(
            checkInConfigDataSource = FakeCheckInConfigDataSource(),
            remoteUserSyncDataSource = remote,
            gson = Gson()
        )
    }

    private fun wordBookDeleteRecord(bookId: Long): OutboxRecord {
        return OutboxRecord(
            id = "record-$bookId",
            aggregate = OutboxTopic.WORD_BOOK_DELETE,
            key = "word_book_delete:$bookId",
            operation = SyncOperation.DELETE,
            payload = Gson().toJson(WordBookDeleteSyncPayload(bookId = bookId)),
            createdAtEpochMillis = 1L
        )
    }

    private class FakeRemoteUserSyncDataSource(
        private val removeResult: Result<Unit>
    ) : RemoteUserSyncDataSource {
        val removedBookIds = mutableListOf<Long>()

        override suspend fun removeMyWordBook(bookId: Long): Result<Unit> {
            removedBookIds += bookId
            return removeResult
        }

        override suspend fun getOnboardingState(): Result<OnboardingSnapshot?> = unexpected()
        override suspend fun updateOnboardingState(snapshot: OnboardingSnapshot): Result<Unit> = unexpected()
        override suspend fun getStudyPlan(): Result<StudyPlan?> = unexpected()
        override suspend fun updateStudyPlan(studyPlan: StudyPlan): Result<Unit> = unexpected()
        override suspend fun getMyWordBooks(): Result<List<WordBookDto>> = unexpected()
        override suspend fun addMyWordBook(bookId: Long): Result<Unit> = unexpected()
        override suspend fun getWordStates(bookId: Long, page: Int, count: Int): Result<PageData<WordStateDto>> = unexpected()
        override suspend fun addFavorite(favorite: WordFavorites): Result<Unit> = unexpected()
        override suspend fun getFavorites(page: Int, count: Int): Result<PageData<FavoriteDto>> = unexpected()
        override suspend fun removeFavorite(wordId: Long): Result<Unit> = unexpected()
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
        ): Result<Unit> = unexpected()

        override suspend fun deleteWordStatesByBookId(bookId: Long): Result<Unit> = unexpected()
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
        ): Result<Unit> = unexpected()

        override suspend fun getWordBookProgressList(): Result<List<WordBookProgressDto>> = unexpected()
        override suspend fun getCurrentWordBookUpdateCandidate(trigger: String): Result<WordBookUpdateCandidateDto?> = unexpected()
        override suspend fun reportCurrentWordBookUpdateAction(request: WordBookUpdateActionRequest): Result<Unit> = unexpected()
        override suspend fun getCurrentWordBookUpdateManifest(version: Long): Result<WordBookUpdateManifestDto> = unexpected()
        override suspend fun getCurrentWordBookUpdateWords(version: Long, page: Int, count: Int): Result<PageData<WordDto>> = unexpected()
        override suspend fun completeCurrentWordBookUpdate(version: Long): Result<Unit> = unexpected()
        override suspend fun getPendingWordBookUpdate(bookId: Long): Result<PendingWordBookUpdateDto?> = unexpected()
        override suspend fun ignoreWordBookUpdate(bookId: Long, version: Long): Result<Unit> = unexpected()
        override suspend fun getWordBookUpdateManifest(bookId: Long, version: Long): Result<WordBookUpdateManifestDto> = unexpected()
        override suspend fun getWordBookUpdateWords(bookId: Long, version: Long, page: Int, count: Int): Result<PageData<WordDto>> = unexpected()
        override suspend fun completeWordBookUpdate(bookId: Long, version: Long): Result<Unit> = unexpected()
        override suspend fun appendStudyRecord(date: String, wordId: Long, word: String, definition: String, isNewWord: Boolean): Result<Unit> = unexpected()
        override suspend fun getStudyRecords(page: Int, count: Int): Result<PageData<StudyRecordDto>> = unexpected()
        override suspend fun getDailyStudyDurations(page: Int, count: Int): Result<PageData<DailyStudyDurationDto>> = unexpected()
        override suspend fun upsertDailyStudyDuration(
            date: String,
            totalDurationMs: Long,
            updatedAt: Long,
            isNewPlanCompleted: Boolean,
            isReviewPlanCompleted: Boolean
        ): Result<Unit> = unexpected()

        override suspend fun setCurrentWordBookSelection(bookId: Long): Result<Unit> = unexpected()
        override suspend fun getCheckInConfig(): Result<CheckInConfigDto?> = unexpected()
        override suspend fun updateCheckInConfig(dayBoundaryOffsetMinutes: Int, timezoneId: String): Result<Unit> = unexpected()
        override suspend fun getCheckInStatus(): Result<CheckInStatusDto?> = unexpected()
        override suspend fun getCheckInRecords(page: Int, count: Int): Result<PageData<CheckInRecordDto>> = unexpected()
        override suspend fun upsertCheckInRecord(date: String, type: String, signedAt: Long, updatedAt: Long): Result<Unit> = unexpected()

        private fun unexpected(): Nothing {
            throw AssertionError("Unexpected remote call")
        }
    }

    private class FakeCheckInConfigDataSource : CheckInConfigDataSource {
        override fun getConfig(): CheckInConfig = CheckInConfig()
        override fun getConfigFlow(): Flow<CheckInConfig> = flowOf(CheckInConfig())
        override fun saveDayBoundaryOffsetMinutes(offsetMinutes: Int) = Unit
        override fun saveTimezoneId(timezoneId: String) = Unit
        override fun saveCachedMakeupCardBalance(balance: Int) = Unit
        override fun consumeCachedMakeupCardBalance(count: Int) = Unit
        override fun saveLastCheckInSyncAt(timestamp: Long) = Unit
        override fun clearUserScopedState() = Unit
    }
}
