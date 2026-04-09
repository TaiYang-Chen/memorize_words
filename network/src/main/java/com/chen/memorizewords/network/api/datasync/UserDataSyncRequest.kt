package com.chen.memorizewords.network.api.datasync

import com.chen.memorizewords.network.api.NetworkRequestExecutor
import com.chen.memorizewords.network.dto.wordbook.WordBookDto
import com.chen.memorizewords.network.dto.wordbook.WordDto
import com.chen.memorizewords.network.dto.wordstate.WordStateDto
import com.chen.memorizewords.network.model.ApiResponse
import com.chen.memorizewords.network.model.PageData
import com.chen.memorizewords.network.util.NetworkResult
import com.chen.memorizewords.network.util.await
import com.chen.memorizewords.network.util.awaitNullable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataSyncRequest @Inject constructor(
    private val apiService: UserDataSyncApiService,
    private val requestExecutor: NetworkRequestExecutor
) {
    suspend fun getStudyPlan(): NetworkResult<StudyPlanDto?> = requestExecutor.executeAuthenticated {
        apiService.getStudyPlan()
            .awaitNullable<ApiResponse<StudyPlanDto?>, StudyPlanDto>()
    }

    suspend fun updateStudyPlan(request: StudyPlanDto): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.updateStudyPlan(request)
            .await<ApiResponse<Unit>, Unit>()
    }

    suspend fun getMyWordBooks(): NetworkResult<List<WordBookDto>> = requestExecutor.executeAuthenticated {
        apiService.getMyWordBooks()
            .await<ApiResponse<List<WordBookDto>>, List<WordBookDto>>()
    }

    suspend fun addMyWordBook(bookId: Long): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.addMyWordBook(AddMyWordBookRequest(bookId))
            .await<ApiResponse<Unit>, Unit>()
    }

    suspend fun getWordStates(
        bookId: Long,
        page: Int,
        count: Int
    ): NetworkResult<PageData<WordStateDto>> = requestExecutor.executeAuthenticated {
        apiService.getWordStates(bookId = bookId, page = page, count = count)
            .await<ApiResponse<PageData<WordStateDto>>, PageData<WordStateDto>>()
    }

    suspend fun addFavorite(
        wordId: Long,
        word: String,
        definitions: String,
        phonetic: String?,
        addedDate: String
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.addFavorite(
            FavoriteSyncRequest(
                wordId = wordId,
                word = word,
                definitions = definitions,
                phonetic = phonetic,
                addedDate = addedDate
            )
        ).await<ApiResponse<Unit>, Unit>()
    }

    suspend fun getFavorites(
        page: Int,
        count: Int
    ): NetworkResult<PageData<FavoriteDto>> = requestExecutor.executeAuthenticated {
        apiService.getFavorites(page = page, count = count)
            .await<ApiResponse<PageData<FavoriteDto>>, PageData<FavoriteDto>>()
    }

    suspend fun removeFavorite(wordId: Long): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.removeFavorite(wordId)
            .await<ApiResponse<Unit>, Unit>()
    }

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
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.upsertWordState(
            bookId = bookId,
            wordId = wordId,
            request = WordStateSyncRequest(
                totalLearnCount = totalLearnCount,
                lastLearnTime = lastLearnTime,
                nextReviewTime = nextReviewTime,
                masteryLevel = masteryLevel,
                userStatus = userStatus,
                repetition = repetition,
                interval = interval,
                efactor = efactor
            )
        ).await<ApiResponse<Unit>, Unit>()
    }

    suspend fun deleteWordStatesByBookId(bookId: Long): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.deleteWordStatesByBookId(bookId)
            .await<ApiResponse<Unit>, Unit>()
    }

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
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.upsertWordBookProgress(
            bookId = bookId,
            request = WordBookProgressSyncRequest(
                bookName = bookName,
                learnedCount = learnedCount,
                masteredCount = masteredCount,
                totalCount = totalCount,
                correctCount = correctCount,
                wrongCount = wrongCount,
                studyDayCount = studyDayCount,
                lastStudyDate = lastStudyDate
            )
        ).await<ApiResponse<Unit>, Unit>()
    }

    suspend fun getWordBookProgressList(): NetworkResult<List<WordBookProgressDto>> =
        requestExecutor.executeAuthenticated {
            apiService.getWordBookProgressList()
                .await<ApiResponse<List<WordBookProgressDto>>, List<WordBookProgressDto>>()
        }

    suspend fun getPendingWordBookUpdate(bookId: Long): NetworkResult<PendingWordBookUpdateDto?> =
        requestExecutor.executeAuthenticated {
            apiService.getPendingWordBookUpdate(bookId)
                .awaitNullable<ApiResponse<PendingWordBookUpdateDto?>, PendingWordBookUpdateDto>()
        }

    suspend fun ignoreWordBookUpdate(bookId: Long, version: Long): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated {
            apiService.ignoreWordBookUpdate(bookId, version)
                .await<ApiResponse<Unit>, Unit>()
        }

    suspend fun getWordBookUpdateManifest(
        bookId: Long,
        version: Long
    ): NetworkResult<WordBookUpdateManifestDto> = requestExecutor.executeAuthenticated {
        apiService.getWordBookUpdateManifest(bookId, version)
            .await<ApiResponse<WordBookUpdateManifestDto>, WordBookUpdateManifestDto>()
    }

    suspend fun getWordBookUpdateWords(
        bookId: Long,
        version: Long,
        page: Int,
        count: Int
    ): NetworkResult<PageData<WordDto>> = requestExecutor.executeAuthenticated {
        apiService.getWordBookUpdateWords(bookId, version, page, count)
            .await<ApiResponse<PageData<WordDto>>, PageData<WordDto>>()
    }

    suspend fun completeWordBookUpdate(bookId: Long, version: Long): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated {
            apiService.completeWordBookUpdate(bookId, version)
                .await<ApiResponse<Unit>, Unit>()
        }

    suspend fun getCurrentWordBookUpdateCandidate(
        trigger: String
    ): NetworkResult<WordBookUpdateCandidateDto?> = requestExecutor.executeAuthenticated {
        apiService.getCurrentWordBookUpdateCandidate(trigger)
            .awaitNullable<ApiResponse<WordBookUpdateCandidateDto?>, WordBookUpdateCandidateDto>()
    }

    suspend fun reportCurrentWordBookUpdateAction(
        action: WordBookUpdateActionRequest
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.reportCurrentWordBookUpdateAction(action)
            .await<ApiResponse<Unit>, Unit>()
    }

    suspend fun getCurrentWordBookUpdateManifest(
        version: Long
    ): NetworkResult<WordBookUpdateManifestDto> = requestExecutor.executeAuthenticated {
        apiService.getCurrentWordBookUpdateManifest(version)
            .await<ApiResponse<WordBookUpdateManifestDto>, WordBookUpdateManifestDto>()
    }

    suspend fun getCurrentWordBookUpdateWords(
        version: Long,
        page: Int,
        count: Int
    ): NetworkResult<PageData<WordDto>> = requestExecutor.executeAuthenticated {
        apiService.getCurrentWordBookUpdateWords(version, page, count)
            .await<ApiResponse<PageData<WordDto>>, PageData<WordDto>>()
    }

    suspend fun completeCurrentWordBookUpdate(version: Long): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated {
            apiService.completeCurrentWordBookUpdate(version)
                .await<ApiResponse<Unit>, Unit>()
        }

    suspend fun appendStudyRecord(
        date: String,
        wordId: Long,
        word: String,
        definition: String,
        isNewWord: Boolean
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.appendStudyRecord(
            StudyRecordSyncRequest(
                date = date,
                wordId = wordId,
                word = word,
                definition = definition,
                isNewWord = isNewWord
            )
        ).await<ApiResponse<Unit>, Unit>()
    }

    suspend fun getStudyRecords(
        page: Int,
        count: Int
    ): NetworkResult<PageData<StudyRecordDto>> = requestExecutor.executeAuthenticated {
        apiService.getStudyRecords(page = page, count = count)
            .await<ApiResponse<PageData<StudyRecordDto>>, PageData<StudyRecordDto>>()
    }

    suspend fun getDailyStudyDurations(
        page: Int,
        count: Int
    ): NetworkResult<PageData<DailyStudyDurationDto>> = requestExecutor.executeAuthenticated {
        apiService.getDailyStudyDurations(page = page, count = count)
            .await<ApiResponse<PageData<DailyStudyDurationDto>>, PageData<DailyStudyDurationDto>>()
    }

    suspend fun upsertDailyStudyDuration(
        date: String,
        totalDurationMs: Long,
        updatedAt: Long,
        isNewPlanCompleted: Boolean,
        isReviewPlanCompleted: Boolean
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.upsertDailyStudyDuration(
            date = date,
            request = DailyStudyDurationSyncRequest(
                totalDurationMs = totalDurationMs,
                updatedAt = updatedAt,
                isNewPlanCompleted = isNewPlanCompleted,
                isReviewPlanCompleted = isReviewPlanCompleted
            )
        ).await<ApiResponse<Unit>, Unit>()
    }

    suspend fun setCurrentWordBookSelection(bookId: Long): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated {
            apiService.setCurrentWordBookSelection(
                bookId = bookId,
                request = WordBookSelectionSyncRequest(selected = true)
            ).await<ApiResponse<Unit>, Unit>()
        }

    suspend fun getCheckInConfig(): NetworkResult<CheckInConfigDto?> = requestExecutor.executeAuthenticated {
        apiService.getCheckInConfig()
            .awaitNullable<ApiResponse<CheckInConfigDto?>, CheckInConfigDto>()
    }

    suspend fun updateCheckInConfig(
        dayBoundaryOffsetMinutes: Int,
        timezoneId: String
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.updateCheckInConfig(
            CheckInConfigDto(
                dayBoundaryOffsetMinutes = dayBoundaryOffsetMinutes,
                timezoneId = timezoneId
            )
        ).await<ApiResponse<Unit>, Unit>()
    }

    suspend fun getCheckInStatus(): NetworkResult<CheckInStatusDto?> = requestExecutor.executeAuthenticated {
        apiService.getCheckInStatus()
            .awaitNullable<ApiResponse<CheckInStatusDto?>, CheckInStatusDto>()
    }

    suspend fun getCheckInRecords(
        page: Int,
        count: Int
    ): NetworkResult<PageData<CheckInRecordDto>> = requestExecutor.executeAuthenticated {
        apiService.getCheckInRecords(page = page, count = count)
            .await<ApiResponse<PageData<CheckInRecordDto>>, PageData<CheckInRecordDto>>()
    }

    suspend fun upsertCheckInRecord(
        date: String,
        type: String,
        signedAt: Long,
        updatedAt: Long
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.upsertCheckInRecord(
            date = date,
            request = CheckInRecordSyncRequest(
                type = type,
                signedAt = signedAt,
                updatedAt = updatedAt
            )
        ).await<ApiResponse<Unit>, Unit>()
    }
}
