package com.chen.memorizewords.data.wordbook.sync

import com.chen.memorizewords.core.common.calendar.CheckInConfigDataSource
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.sync.CheckInRecordSyncPayload
import com.chen.memorizewords.domain.sync.DailyStudyDurationSyncPayload
import com.chen.memorizewords.domain.sync.FavoriteSyncPayload
import com.chen.memorizewords.domain.sync.OnboardingStateSyncPayload
import com.chen.memorizewords.domain.sync.OutboxRecord
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.StudyPlanSyncPayload
import com.chen.memorizewords.domain.sync.StudyRecordSyncPayload
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxHandler
import com.chen.memorizewords.domain.sync.WordBookProgressSyncPayload
import com.chen.memorizewords.domain.sync.WordBookSelectionSyncPayload
import com.chen.memorizewords.domain.sync.WordStateDeleteByBookSyncPayload
import com.chen.memorizewords.domain.sync.WordStateUpsertSyncPayload
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
        OutboxTopic.STUDY_RECORD,
        OutboxTopic.DAILY_STUDY_DURATION,
        OutboxTopic.FAVORITE,
        OutboxTopic.WORD_BOOK_PROGRESS,
        OutboxTopic.WORD_STATE_UPSERT,
        OutboxTopic.WORD_STATE_DELETE_BY_BOOK,
        OutboxTopic.WORD_BOOK_SELECTION,
        OutboxTopic.STUDY_PLAN,
        OutboxTopic.ONBOARDING_STATE,
        OutboxTopic.CHECKIN_RECORD
    )

    override suspend fun handle(record: OutboxRecord) {
        when (record.aggregate) {
            OutboxTopic.STUDY_RECORD -> {
                val payload = gson.fromJson(record.payload, StudyRecordSyncPayload::class.java)
                remoteUserSyncDataSource.appendStudyRecord(
                    date = payload.date,
                    wordId = payload.wordId,
                    word = payload.word,
                    definition = payload.definition,
                    isNewWord = payload.isNewWord
                ).getOrThrow()
            }

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

            OutboxTopic.WORD_BOOK_PROGRESS -> {
                val payload = gson.fromJson(record.payload, WordBookProgressSyncPayload::class.java)
                remoteUserSyncDataSource.upsertWordBookProgress(
                    bookId = payload.bookId,
                    bookName = payload.bookName,
                    learnedCount = payload.learnedCount,
                    masteredCount = payload.masteredCount,
                    totalCount = payload.totalCount,
                    correctCount = payload.correctCount,
                    wrongCount = payload.wrongCount,
                    studyDayCount = payload.studyDayCount,
                    lastStudyDate = payload.lastStudyDate
                ).getOrThrow()
            }

            OutboxTopic.WORD_STATE_UPSERT -> {
                val payload = gson.fromJson(record.payload, WordStateUpsertSyncPayload::class.java)
                remoteUserSyncDataSource.upsertWordState(
                    bookId = payload.bookId,
                    wordId = payload.wordId,
                    totalLearnCount = payload.totalLearnCount,
                    lastLearnTime = payload.lastLearnTime,
                    nextReviewTime = payload.nextReviewTime,
                    masteryLevel = payload.masteryLevel,
                    userStatus = payload.userStatus,
                    repetition = payload.repetition,
                    interval = payload.interval,
                    efactor = payload.efactor
                ).getOrThrow()
            }

            OutboxTopic.WORD_STATE_DELETE_BY_BOOK -> {
                val payload = gson.fromJson(record.payload, WordStateDeleteByBookSyncPayload::class.java)
                remoteUserSyncDataSource.deleteWordStatesByBookId(payload.bookId).getOrThrow()
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
