package com.chen.memorizewords.data.wordbook.remoteapi.api.datasync

import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordstate.WordStateDto
import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.PageData
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.core.network.http.await
import com.chen.memorizewords.core.network.http.awaitNullable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataSyncRequest @Inject constructor(
    private val apiService: UserDataSyncApiService,
    private val requestExecutor: NetworkRequestExecutor
) {
    suspend fun getOnboardingState(): NetworkResult<OnboardingStateDto?> = requestExecutor.executeAuthenticated {
        apiService.getOnboardingState()
            .awaitNullable<ApiResponse<OnboardingStateDto?>, OnboardingStateDto>()
    }

    suspend fun updateOnboardingState(request: OnboardingStateDto): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated {
            apiService.updateOnboardingState(request)
                .await<ApiResponse<Unit>, Unit>()
        }

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

    suspend fun createMyWordBook(
        title: String,
        category: String,
        description: String,
        words: List<String>
    ): NetworkResult<WordBookDto> = requestExecutor.executeAuthenticated {
        apiService.createMyWordBook(
            CreateMyWordBookRequest(
                title = title,
                category = category,
                description = description,
                words = words
            )
        ).await<ApiResponse<WordBookDto>, WordBookDto>()
    }

    suspend fun removeMyWordBook(bookId: Long): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.removeMyWordBook(bookId)
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

    suspend fun completeCurrentWordBookUpdate(version: Long): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated {
            apiService.completeCurrentWordBookUpdate(version)
                .await<ApiResponse<Unit>, Unit>()
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
