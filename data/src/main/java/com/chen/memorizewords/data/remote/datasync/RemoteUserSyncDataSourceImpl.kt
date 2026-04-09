package com.chen.memorizewords.data.remote.datasync

import com.chen.memorizewords.data.remote.RemoteResultAdapter
import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.domain.model.study.StudyPlan
import com.chen.memorizewords.domain.model.study.favorites.WordFavorites
import com.chen.memorizewords.domain.repository.WordOrderType
import com.chen.memorizewords.network.api.datasync.CheckInConfigDto
import com.chen.memorizewords.network.api.datasync.CheckInRecordDto
import com.chen.memorizewords.network.api.datasync.CheckInStatusDto
import com.chen.memorizewords.network.api.datasync.DailyStudyDurationDto
import com.chen.memorizewords.network.api.datasync.FavoriteDto
import com.chen.memorizewords.network.api.datasync.PendingWordBookUpdateDto
import com.chen.memorizewords.network.api.datasync.StudyRecordDto
import com.chen.memorizewords.network.api.datasync.StudyPlanDto
import com.chen.memorizewords.network.api.datasync.UserDataSyncRequest
import com.chen.memorizewords.network.api.datasync.WordBookUpdateActionRequest
import com.chen.memorizewords.network.api.datasync.WordBookUpdateCandidateDto
import com.chen.memorizewords.network.api.datasync.WordBookUpdateManifestDto
import com.chen.memorizewords.network.api.datasync.WordBookProgressDto
import com.chen.memorizewords.network.dto.wordbook.WordBookDto
import com.chen.memorizewords.network.dto.wordbook.WordDto
import com.chen.memorizewords.network.dto.wordstate.WordStateDto
import com.chen.memorizewords.network.model.PageData
import javax.inject.Inject

class RemoteUserSyncDataSourceImpl @Inject constructor(
    private val request: UserDataSyncRequest,
    private val remoteResultAdapter: RemoteResultAdapter
) : RemoteUserSyncDataSource {

    override suspend fun getStudyPlan(): Result<StudyPlan?> {
        return remoteResultAdapter.toResult { request.getStudyPlan() }
            .map { dto ->
                dto?.let {
                    StudyPlan(
                        dailyNewCount = it.dailyNewWords,
                        dailyReviewCount = it.dailyReviewWords,
                        testMode = runCatching { LearningTestMode.valueOf(it.testMode) }
                            .getOrDefault(LearningTestMode.MEANING_CHOICE),
                        wordOrderType = runCatching { WordOrderType.valueOf(it.wordOrderType) }
                            .getOrDefault(WordOrderType.RANDOM)
                    )
                }
            }
    }

    override suspend fun updateStudyPlan(studyPlan: StudyPlan): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.updateStudyPlan(
                StudyPlanDto(
                    dailyNewWords = studyPlan.dailyNewCount,
                    dailyReviewWords = studyPlan.dailyReviewCount,
                    testMode = studyPlan.testMode.name,
                    wordOrderType = studyPlan.wordOrderType.name
                )
            )
        }
    }

    override suspend fun getMyWordBooks(): Result<List<WordBookDto>> {
        return remoteResultAdapter.toResult { request.getMyWordBooks() }
    }

    override suspend fun addMyWordBook(bookId: Long): Result<Unit> {
        return remoteResultAdapter.toResult { request.addMyWordBook(bookId) }
    }

    override suspend fun getWordStates(
        bookId: Long,
        page: Int,
        count: Int
    ): Result<PageData<WordStateDto>> {
        return remoteResultAdapter.toResult {
            request.getWordStates(bookId = bookId, page = page, count = count)
        }
    }

    override suspend fun addFavorite(favorite: WordFavorites): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.addFavorite(
                wordId = favorite.wordId,
                word = favorite.word,
                definitions = favorite.definitions,
                phonetic = favorite.phonetic,
                addedDate = favorite.addedDate
            )
        }
    }

    override suspend fun getFavorites(page: Int, count: Int): Result<PageData<FavoriteDto>> {
        return remoteResultAdapter.toResult { request.getFavorites(page = page, count = count) }
    }

    override suspend fun removeFavorite(wordId: Long): Result<Unit> {
        return remoteResultAdapter.toResult { request.removeFavorite(wordId) }
    }

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
    ): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.upsertWordState(
                bookId = bookId,
                wordId = wordId,
                totalLearnCount = totalLearnCount,
                lastLearnTime = lastLearnTime,
                nextReviewTime = nextReviewTime,
                masteryLevel = masteryLevel,
                userStatus = userStatus,
                repetition = repetition,
                interval = interval,
                efactor = efactor
            )
        }
    }

    override suspend fun deleteWordStatesByBookId(bookId: Long): Result<Unit> {
        return remoteResultAdapter.toResult { request.deleteWordStatesByBookId(bookId) }
    }

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
    ): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.upsertWordBookProgress(
                bookId = bookId,
                bookName = bookName,
                learnedCount = learnedCount,
                masteredCount = masteredCount,
                totalCount = totalCount,
                correctCount = correctCount,
                wrongCount = wrongCount,
                studyDayCount = studyDayCount,
                lastStudyDate = lastStudyDate
            )
        }
    }

    override suspend fun getWordBookProgressList(): Result<List<WordBookProgressDto>> {
        return remoteResultAdapter.toResult { request.getWordBookProgressList() }
    }

    override suspend fun getCurrentWordBookUpdateCandidate(
        trigger: String
    ): Result<WordBookUpdateCandidateDto?> {
        return remoteResultAdapter.toResult { request.getCurrentWordBookUpdateCandidate(trigger) }
    }

    override suspend fun reportCurrentWordBookUpdateAction(
        request: WordBookUpdateActionRequest
    ): Result<Unit> {
        return remoteResultAdapter.toResult {
            this.request.reportCurrentWordBookUpdateAction(request)
        }
    }

    override suspend fun getCurrentWordBookUpdateManifest(
        version: Long
    ): Result<WordBookUpdateManifestDto> {
        return remoteResultAdapter.toResult { request.getCurrentWordBookUpdateManifest(version) }
    }

    override suspend fun getCurrentWordBookUpdateWords(
        version: Long,
        page: Int,
        count: Int
    ): Result<PageData<WordDto>> {
        return remoteResultAdapter.toResult {
            request.getCurrentWordBookUpdateWords(version, page, count)
        }
    }

    override suspend fun completeCurrentWordBookUpdate(version: Long): Result<Unit> {
        return remoteResultAdapter.toResult { request.completeCurrentWordBookUpdate(version) }
    }

    override suspend fun getPendingWordBookUpdate(bookId: Long): Result<PendingWordBookUpdateDto?> {
        return remoteResultAdapter.toResult { request.getPendingWordBookUpdate(bookId) }
    }

    override suspend fun ignoreWordBookUpdate(bookId: Long, version: Long): Result<Unit> {
        return remoteResultAdapter.toResult { request.ignoreWordBookUpdate(bookId, version) }
    }

    override suspend fun getWordBookUpdateManifest(
        bookId: Long,
        version: Long
    ): Result<WordBookUpdateManifestDto> {
        return remoteResultAdapter.toResult { request.getWordBookUpdateManifest(bookId, version) }
    }

    override suspend fun getWordBookUpdateWords(
        bookId: Long,
        version: Long,
        page: Int,
        count: Int
    ): Result<PageData<WordDto>> {
        return remoteResultAdapter.toResult {
            request.getWordBookUpdateWords(bookId, version, page, count)
        }
    }

    override suspend fun completeWordBookUpdate(bookId: Long, version: Long): Result<Unit> {
        return remoteResultAdapter.toResult { request.completeWordBookUpdate(bookId, version) }
    }

    override suspend fun appendStudyRecord(
        date: String,
        wordId: Long,
        word: String,
        definition: String,
        isNewWord: Boolean
    ): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.appendStudyRecord(
                date = date,
                wordId = wordId,
                word = word,
                definition = definition,
                isNewWord = isNewWord
            )
        }
    }

    override suspend fun getStudyRecords(
        page: Int,
        count: Int
    ): Result<PageData<StudyRecordDto>> {
        return remoteResultAdapter.toResult { request.getStudyRecords(page = page, count = count) }
    }

    override suspend fun getDailyStudyDurations(
        page: Int,
        count: Int
    ): Result<PageData<DailyStudyDurationDto>> {
        return remoteResultAdapter.toResult { request.getDailyStudyDurations(page = page, count = count) }
    }

    override suspend fun upsertDailyStudyDuration(
        date: String,
        totalDurationMs: Long,
        updatedAt: Long,
        isNewPlanCompleted: Boolean,
        isReviewPlanCompleted: Boolean
    ): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.upsertDailyStudyDuration(
                date = date,
                totalDurationMs = totalDurationMs,
                updatedAt = updatedAt,
                isNewPlanCompleted = isNewPlanCompleted,
                isReviewPlanCompleted = isReviewPlanCompleted
            )
        }
    }

    override suspend fun setCurrentWordBookSelection(bookId: Long): Result<Unit> {
        return remoteResultAdapter.toResult { request.setCurrentWordBookSelection(bookId) }
    }

    override suspend fun getCheckInConfig(): Result<CheckInConfigDto?> {
        return remoteResultAdapter.toResult { request.getCheckInConfig() }
    }

    override suspend fun updateCheckInConfig(
        dayBoundaryOffsetMinutes: Int,
        timezoneId: String
    ): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.updateCheckInConfig(
                dayBoundaryOffsetMinutes = dayBoundaryOffsetMinutes,
                timezoneId = timezoneId
            )
        }
    }

    override suspend fun getCheckInStatus(): Result<CheckInStatusDto?> {
        return remoteResultAdapter.toResult { request.getCheckInStatus() }
    }

    override suspend fun getCheckInRecords(
        page: Int,
        count: Int
    ): Result<PageData<CheckInRecordDto>> {
        return remoteResultAdapter.toResult { request.getCheckInRecords(page = page, count = count) }
    }

    override suspend fun upsertCheckInRecord(
        date: String,
        type: String,
        signedAt: Long,
        updatedAt: Long
    ): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.upsertCheckInRecord(
                date = date,
                type = type,
                signedAt = signedAt,
                updatedAt = updatedAt
            )
        }
    }
}
