package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.common.calendar.CheckInConfigDataSource
import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.core.network.http.BodyPolicy
import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventEntity
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingDisplayRecordSyncRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingSettingsSyncRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningEventRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningEventResultDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningProgressDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningSyncApiService
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningWordStateDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeDurationSyncRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeSessionSyncRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeSettingsSyncRequest
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.CheckInRecordSyncRequest
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.DailyStudyDurationSyncRequest
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.FavoriteSyncRequest
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.OnboardingStateDto
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.StudyPlanDto
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.UserDataSyncApiService
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.WordBookSelectionSyncRequest
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.learning.BookLearningWriteCoordinator
import com.chen.memorizewords.domain.study.repository.learning.LearningEventSyncResultSnapshot
import com.chen.memorizewords.domain.study.repository.learning.LearningSyncStatePort
import com.chen.memorizewords.domain.sync.FailureQueueEventType
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress
import com.squareup.moshi.Moshi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class FailedSyncEventReplayer @Inject constructor(
    private val learningApi: LearningSyncApiService,
    private val userApi: UserDataSyncApiService,
    private val executor: NetworkRequestExecutor,
    private val latestSyncRequestCoordinator: LatestSyncRequestCoordinator,
    private val learningSyncStatePort: LearningSyncStatePort,
    private val bookLearningWriteCoordinator: BookLearningWriteCoordinator,
    private val checkInConfigDataSource: CheckInConfigDataSource,
    private val moshi: Moshi,
    private val sessionGate: FailureQueueSessionGate,
    private val directSyncLauncher: DirectSyncLauncher
) {
    suspend fun replay(
        event: FailedSyncEventEntity,
        sessionToken: FailureQueueSessionToken
    ): ReplayOutcome {
        if (!sessionGate.isCurrent(sessionToken)) return ReplayOutcome.SessionInvalidated
        return directSyncLauncher.withOrdering(event.orderingKey) {
            latestSyncRequestCoordinator.replay(event) {
                replayClaimed(event, sessionToken)
            }
        }
    }

    private suspend fun replayClaimed(
        event: FailedSyncEventEntity,
        sessionToken: FailureQueueSessionToken
    ): ReplayOutcome {
        if (!sessionGate.isCurrent(sessionToken)) return ReplayOutcome.SessionInvalidated
        if (event.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return ReplayOutcome.Blocked("unsupported schema ${event.schemaVersion}")
        }
        return try {
            when (event.eventType) {
                FailureQueueEventType.LEARNING_EVENT -> replayLearning(event, sessionToken)
                FailureQueueEventType.PRACTICE_SETTINGS -> unitOutcome(
                    executor.executeQueuedRetry(
                        learningApi.updatePracticeSettings(parse(event, PracticeSettingsSyncRequest::class.java)),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.PRACTICE_DURATION -> unitOutcome(
                    executor.executeQueuedRetry(
                        learningApi.upsertPracticeDuration(
                            scalar(event, "date"),
                            parse(event, PracticeDurationSyncRequest::class.java)
                        ),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.PRACTICE_SESSION -> unitOutcome(
                    executor.executeQueuedRetry(
                        learningApi.appendPracticeSession(parse(event, PracticeSessionSyncRequest::class.java)),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.FLOATING_SETTINGS -> unitOutcome(
                    executor.executeQueuedRetry(
                        learningApi.updateFloatingSettings(parse(event, FloatingSettingsSyncRequest::class.java)),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.FLOATING_DISPLAY_RECORD -> unitOutcome(
                    executor.executeQueuedRetry(
                        learningApi.upsertFloatingDisplayRecord(
                            scalar(event, "date"),
                            parse(event, FloatingDisplayRecordSyncRequest::class.java)
                        ),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.ONBOARDING_STATE -> unitOutcome(
                    executor.executeQueuedRetry(
                        userApi.updateOnboardingState(parse(event, OnboardingStateDto::class.java)),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.STUDY_PLAN -> unitOutcome(
                    executor.executeQueuedRetry(
                        userApi.updateStudyPlan(parse(event, StudyPlanDto::class.java)),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.FAVORITE_ADD -> unitOutcome(
                    executor.executeQueuedRetry(
                        userApi.addFavorite(parse(event, FavoriteSyncRequest::class.java)),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.FAVORITE_REMOVE -> unitOutcome(
                    executor.executeQueuedRetry(
                        userApi.removeFavorite(longScalar(event, "wordId")),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.WORD_BOOK_DELETE -> unitOutcome(
                    executor.executeQueuedRetry(
                        userApi.removeMyWordBook(longScalar(event, "bookId")),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.WORD_BOOK_SELECTION -> unitOutcome(
                    executor.executeQueuedRetry(
                        userApi.setCurrentWordBookSelection(
                            longScalar(event, "bookId"),
                            parse(event, WordBookSelectionSyncRequest::class.java)
                        ),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.DAILY_STUDY_DURATION -> unitOutcome(
                    executor.executeQueuedRetry(
                        userApi.upsertDailyStudyDuration(
                            scalar(event, "date"),
                            parse(event, DailyStudyDurationSyncRequest::class.java)
                        ),
                        BodyPolicy.UnitBody
                    )
                )
                FailureQueueEventType.CHECKIN_RECORD -> replayCheckIn(event, sessionToken)
                else -> ReplayOutcome.Blocked("missing replay handler for ${event.eventType}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            ReplayOutcome.Blocked("invalid params: ${exception.message.orEmpty()}")
        }
    }

    private suspend fun replayLearning(
        event: FailedSyncEventEntity,
        sessionToken: FailureQueueSessionToken
    ): ReplayOutcome {
        return when (val result = executor.executeQueuedRetry(
            learningApi.recordLearningEvent(parse(event, LearningEventRequest::class.java))
        )) {
            is NetworkResult.Success -> {
                if (!sessionGate.isCurrent(sessionToken)) return ReplayOutcome.SessionInvalidated
                bookLearningWriteCoordinator.withBookWrite(eventBookId(event)) {
                    if (!sessionGate.isCurrent(sessionToken)) {
                        return@withBookWrite
                    }
                    learningSyncStatePort.applyLearningEventSyncResult(result.data.toSnapshot())
                }
                if (!sessionGate.isCurrent(sessionToken)) return ReplayOutcome.SessionInvalidated
                ReplayOutcome.Success
            }
            is NetworkResult.Failure -> result.toOutcome()
        }
    }

    private suspend fun replayCheckIn(
        event: FailedSyncEventEntity,
        sessionToken: FailureQueueSessionToken
    ): ReplayOutcome {
        val request = parse(event, CheckInRecordSyncRequest::class.java)
        val outcome = unitOutcome(
            executor.executeQueuedRetry(
                userApi.upsertCheckInRecord(scalar(event, "date"), request),
                BodyPolicy.UnitBody
            )
        )
        if (!sessionGate.isCurrent(sessionToken)) return ReplayOutcome.SessionInvalidated
        if (outcome == ReplayOutcome.Success && request.type == "MAKEUP") {
            checkInConfigDataSource.consumeCachedMakeupCardBalance()
        }
        return outcome
    }

    private fun unitOutcome(result: NetworkResult<Unit>): ReplayOutcome {
        return when (result) {
            is NetworkResult.Success -> ReplayOutcome.Success
            is NetworkResult.Failure -> result.toOutcome()
        }
    }

    private fun NetworkResult.Failure.toOutcome(): ReplayOutcome {
        return if (this is NetworkResult.Failure.NetworkError && throwable is IOException) {
            ReplayOutcome.NetworkFailure(throwable.message.orEmpty().ifBlank { "network failure" })
        } else {
            ReplayOutcome.Blocked(toString())
        }
    }

    private fun <T> parse(event: FailedSyncEventEntity, type: Class<T>): T {
        return moshi.adapter(type).fromJson(event.paramsJson)
            ?: throw IllegalArgumentException("params decode returned null")
    }

    @Suppress("UNCHECKED_CAST")
    private fun params(event: FailedSyncEventEntity): Map<String, Any?> {
        return moshi.adapter(Map::class.java).fromJson(event.paramsJson) as? Map<String, Any?>
            ?: emptyMap()
    }

    private fun scalar(event: FailedSyncEventEntity, name: String): String {
        return params(event)[name]?.toString()
            ?: throw IllegalArgumentException("missing parameter $name")
    }

    private fun longScalar(event: FailedSyncEventEntity, name: String): Long {
        return (params(event)[name] as? Number)?.toLong()
            ?: scalar(event, name).toLong()
    }

    private fun eventBookId(event: FailedSyncEventEntity): Long = longScalar(event, "bookId")

    private fun LearningEventResultDto.toSnapshot(): LearningEventSyncResultSnapshot {
        val progress = learningProgress ?: wordBookProgress
        return LearningEventSyncResultSnapshot(
            clientEventId = clientEventId,
            conflict = conflict,
            wordState = wordState?.toDomain(),
            learningProgress = progress?.toDomain(),
            serverStateRevision = wordState?.stateRevision ?: 0L
        )
    }

    private fun LearningWordStateDto.toDomain(): WordLearningState = WordLearningState(
        wordId = wordId,
        bookId = bookId,
        totalLearnCount = totalLearnCount,
        lastLearnedAtMs = lastLearnedAtMs,
        nextReviewAtMs = nextReviewAtMs,
        masteryLevel = masteryLevel,
        userStatus = userStatus,
        repetition = repetition,
        interval = interval,
        efactor = efactor,
        stateRevision = stateRevision
    )

    private fun LearningProgressDto.toDomain(): WordBookProgress = WordBookProgress(
        wordBookId = bookId,
        wordBookName = bookName,
        learningCount = learnedCount,
        masteredCount = masteredCount,
        totalCount = totalCount,
        correctCount = correctCount,
        wrongCount = wrongCount,
        studyDayCount = studyDayCount,
        lastStudyDate = lastStudyDate.orEmpty(),
        revision = revision
    )

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

sealed interface ReplayOutcome {
    data object Success : ReplayOutcome
    data object SessionInvalidated : ReplayOutcome
    data class NetworkFailure(val message: String) : ReplayOutcome
    data class Blocked(val message: String) : ReplayOutcome
}
