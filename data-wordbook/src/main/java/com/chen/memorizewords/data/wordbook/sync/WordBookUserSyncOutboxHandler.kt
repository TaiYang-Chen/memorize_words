package com.chen.memorizewords.data.wordbook.sync

import com.chen.memorizewords.core.common.calendar.CheckInConfigDataSource
import com.chen.memorizewords.core.network.remote.HttpStatusException
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.sync.CheckInRecordSyncPayload
import com.chen.memorizewords.domain.sync.DailyStudyDurationSyncPayload
import com.chen.memorizewords.domain.sync.FavoriteSyncPayload
import com.chen.memorizewords.domain.sync.OnboardingStateSyncPayload
import com.chen.memorizewords.domain.sync.OutboxRecord
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.StudyPlanSyncPayload
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxHandler
import com.chen.memorizewords.domain.sync.WordBookDeleteSyncPayload
import com.chen.memorizewords.domain.sync.WordBookSelectionSyncPayload
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordBookUserSyncOutboxHandler @Inject constructor(
    private val checkInConfigDataSource: CheckInConfigDataSource,
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val gson: Gson
) : SyncOutboxHandler {

    override val topics: Set<String> = setOf(
        OutboxTopic.DAILY_STUDY_DURATION,
        OutboxTopic.FAVORITE,
        OutboxTopic.WORD_BOOK_DELETE,
        OutboxTopic.WORD_BOOK_SELECTION,
        OutboxTopic.STUDY_PLAN,
        OutboxTopic.ONBOARDING_STATE,
        OutboxTopic.CHECKIN_RECORD
    )

    override suspend fun handle(record: OutboxRecord) {
        when (record.aggregate) {
            OutboxTopic.DAILY_STUDY_DURATION -> {
                val payload = gson.fromJson(record.payload, DailyStudyDurationSyncPayload::class.java)
                remoteUserSyncDataSource.upsertDailyStudyDuration(
                    date = payload.date,
                    totalDurationMs = payload.totalDurationMs,
                    updatedAt = payload.updatedAt,
                    isNewPlanCompleted = payload.isNewPlanCompleted,
                    isReviewPlanCompleted = payload.isReviewPlanCompleted
                ).getOrThrow()
            }

            OutboxTopic.FAVORITE -> {
                val payload = gson.fromJson(record.payload, FavoriteSyncPayload::class.java)
                when (record.operation) {
                    SyncOperation.UPSERT -> {
                        remoteUserSyncDataSource.addFavorite(
                            WordFavorites(
                                wordId = payload.wordId,
                                word = payload.word.orEmpty(),
                                definitions = payload.definitions.orEmpty(),
                                phonetic = payload.phonetic,
                                addedDate = payload.addedDate.orEmpty()
                            )
                        ).getOrThrow()
                    }

                    SyncOperation.DELETE -> {
                        remoteUserSyncDataSource.removeFavorite(payload.wordId).getOrThrow()
                    }
                }
            }

            OutboxTopic.WORD_BOOK_DELETE -> {
                val payload = gson.fromJson(record.payload, WordBookDeleteSyncPayload::class.java)
                val result = remoteUserSyncDataSource.removeMyWordBook(payload.bookId)
                if (result.isSuccess || result.exceptionOrNull().isDeletedWordBookAlreadyAbsent()) {
                    return
                }
                result.getOrThrow()
            }

            OutboxTopic.WORD_BOOK_SELECTION -> {
                val payload = gson.fromJson(record.payload, WordBookSelectionSyncPayload::class.java)
                remoteUserSyncDataSource.setCurrentWordBookSelection(payload.bookId).getOrThrow()
            }

            OutboxTopic.STUDY_PLAN -> {
                val payload = gson.fromJson(record.payload, StudyPlanSyncPayload::class.java)
                remoteUserSyncDataSource.updateStudyPlan(
                    StudyPlan(
                        dailyNewCount = payload.dailyNewWords,
                        dailyReviewCount = payload.dailyReviewWords,
                        testMode = runCatching { LearningTestMode.valueOf(payload.testMode) }
                            .getOrDefault(LearningTestMode.MEANING_CHOICE),
                        wordOrderType = runCatching { WordOrderType.valueOf(payload.wordOrderType) }
                            .getOrDefault(WordOrderType.RANDOM)
                    )
                ).getOrThrow()
            }

            OutboxTopic.ONBOARDING_STATE -> {
                val payload = gson.fromJson(record.payload, OnboardingStateSyncPayload::class.java)
                remoteUserSyncDataSource.updateOnboardingState(
                    OnboardingSnapshot(
                        phase = runCatching { OnboardingPhase.valueOf(payload.phase) }
                            .getOrDefault(OnboardingPhase.NEEDS_WORD_BOOK),
                        selectedWordBookId = payload.selectedWordBookId,
                        revision = payload.revision,
                        updatedAt = payload.updatedAt,
                        completedAt = payload.completedAt
                    )
                ).getOrThrow()
            }

            OutboxTopic.CHECKIN_RECORD -> {
                val payload = gson.fromJson(record.payload, CheckInRecordSyncPayload::class.java)
                remoteUserSyncDataSource.upsertCheckInRecord(
                    date = payload.date,
                    type = payload.type,
                    signedAt = payload.signedAt,
                    updatedAt = payload.updatedAt
                ).getOrThrow()
            }
        }
    }

    override suspend fun onSuccess(record: OutboxRecord) {
        if (record.aggregate != OutboxTopic.CHECKIN_RECORD) return
        runCatching {
            gson.fromJson(record.payload, CheckInRecordSyncPayload::class.java)
        }.getOrNull()?.let { payload ->
            if (payload.type == "MAKEUP") {
                checkInConfigDataSource.consumeCachedMakeupCardBalance()
            }
        }
    }
}

private fun Throwable?.isDeletedWordBookAlreadyAbsent(): Boolean {
    return this is HttpStatusException &&
        code == 400 &&
        message.orEmpty().contains("bookId invalid", ignoreCase = true)
}
