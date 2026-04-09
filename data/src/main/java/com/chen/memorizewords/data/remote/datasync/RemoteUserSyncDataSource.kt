package com.chen.memorizewords.data.remote.datasync

import com.chen.memorizewords.domain.model.study.favorites.WordFavorites
import com.chen.memorizewords.network.api.datasync.CheckInConfigDto
import com.chen.memorizewords.network.api.datasync.CheckInRecordDto
import com.chen.memorizewords.network.api.datasync.CheckInStatusDto
import com.chen.memorizewords.network.api.datasync.DailyStudyDurationDto
import com.chen.memorizewords.network.api.datasync.FavoriteDto
import com.chen.memorizewords.network.api.datasync.PendingWordBookUpdateDto
import com.chen.memorizewords.network.api.datasync.StudyRecordDto
import com.chen.memorizewords.network.api.datasync.WordBookUpdateActionRequest
import com.chen.memorizewords.network.api.datasync.WordBookUpdateCandidateDto
import com.chen.memorizewords.network.api.datasync.WordBookUpdateManifestDto
import com.chen.memorizewords.network.api.datasync.WordBookProgressDto
import com.chen.memorizewords.domain.model.study.StudyPlan
import com.chen.memorizewords.network.dto.wordbook.WordBookDto
import com.chen.memorizewords.network.dto.wordbook.WordDto
import com.chen.memorizewords.network.dto.wordstate.WordStateDto
import com.chen.memorizewords.network.model.PageData

interface RemoteUserSyncDataSource {
    suspend fun getStudyPlan(): Result<StudyPlan?>
    suspend fun updateStudyPlan(studyPlan: StudyPlan): Result<Unit>
    suspend fun getMyWordBooks(): Result<List<WordBookDto>>
    suspend fun addMyWordBook(bookId: Long): Result<Unit>
    suspend fun getWordStates(bookId: Long, page: Int, count: Int): Result<PageData<WordStateDto>>
    suspend fun addFavorite(favorite: WordFavorites): Result<Unit>
    suspend fun getFavorites(page: Int, count: Int): Result<PageData<FavoriteDto>>
    suspend fun removeFavorite(wordId: Long): Result<Unit>
    suspend fun upsertWordState(
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
    ): Result<Unit>

    suspend fun deleteWordStatesByBookId(bookId: Long): Result<Unit>
    suspend fun upsertWordBookProgress(
        bookId: Long,
        bookName: String,
        learnedCount: Int,
        masteredCount: Int,
        totalCount: Int,
        correctCount: Int,
        wrongCount: Int,
        studyDayCount: Int,
        lastStudyDate: String
    ): Result<Unit>

    suspend fun getWordBookProgressList(): Result<List<WordBookProgressDto>>
    suspend fun getCurrentWordBookUpdateCandidate(trigger: String): Result<WordBookUpdateCandidateDto?>
    suspend fun reportCurrentWordBookUpdateAction(request: WordBookUpdateActionRequest): Result<Unit>
    suspend fun getCurrentWordBookUpdateManifest(version: Long): Result<WordBookUpdateManifestDto>
    suspend fun getCurrentWordBookUpdateWords(
        version: Long,
        page: Int,
        count: Int
    ): Result<PageData<WordDto>>
    suspend fun completeCurrentWordBookUpdate(version: Long): Result<Unit>
    suspend fun getPendingWordBookUpdate(bookId: Long): Result<PendingWordBookUpdateDto?>
    suspend fun ignoreWordBookUpdate(bookId: Long, version: Long): Result<Unit>
    suspend fun getWordBookUpdateManifest(bookId: Long, version: Long): Result<WordBookUpdateManifestDto>
    suspend fun getWordBookUpdateWords(
        bookId: Long,
        version: Long,
        page: Int,
        count: Int
    ): Result<PageData<WordDto>>
    suspend fun completeWordBookUpdate(bookId: Long, version: Long): Result<Unit>

    suspend fun appendStudyRecord(
        date: String,
        wordId: Long,
        word: String,
        definition: String,
        isNewWord: Boolean
    ): Result<Unit>

    suspend fun getStudyRecords(page: Int, count: Int): Result<PageData<StudyRecordDto>>

    suspend fun getDailyStudyDurations(page: Int, count: Int): Result<PageData<DailyStudyDurationDto>>

    suspend fun upsertDailyStudyDuration(
        date: String,
        totalDurationMs: Long,
        updatedAt: Long,
        isNewPlanCompleted: Boolean,
        isReviewPlanCompleted: Boolean
    ): Result<Unit>

    suspend fun setCurrentWordBookSelection(bookId: Long): Result<Unit>

    suspend fun getCheckInConfig(): Result<CheckInConfigDto?>

    suspend fun updateCheckInConfig(
        dayBoundaryOffsetMinutes: Int,
        timezoneId: String
    ): Result<Unit>

    suspend fun getCheckInStatus(): Result<CheckInStatusDto?>
    suspend fun getCheckInRecords(page: Int, count: Int): Result<PageData<CheckInRecordDto>>
    suspend fun upsertCheckInRecord(
        date: String,
        type: String,
        signedAt: Long,
        updatedAt: Long
    ): Result<Unit>
}
