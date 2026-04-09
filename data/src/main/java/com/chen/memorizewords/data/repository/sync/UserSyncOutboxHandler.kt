package com.chen.memorizewords.data.repository.sync

import com.chen.memorizewords.data.local.mmkv.checkin.CheckInConfigDataSource
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxEntity
import com.chen.memorizewords.data.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.domain.model.study.StudyPlan
import com.chen.memorizewords.domain.model.study.favorites.WordFavorites
import com.chen.memorizewords.domain.repository.WordOrderType
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSyncOutboxHandler @Inject constructor(
    private val checkInConfigDataSource: CheckInConfigDataSource,
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val gson: Gson
) : SyncOutboxHandler {

    override val bizTypes: Set<String> = setOf(
        SyncOutboxBizType.STUDY_RECORD,
        SyncOutboxBizType.DAILY_STUDY_DURATION,
        SyncOutboxBizType.FAVORITE,
        SyncOutboxBizType.WORD_BOOK_PROGRESS,
        SyncOutboxBizType.WORD_STATE_UPSERT,
        SyncOutboxBizType.WORD_STATE_DELETE_BY_BOOK,
        SyncOutboxBizType.WORD_BOOK_SELECTION,
        SyncOutboxBizType.STUDY_PLAN,
        SyncOutboxBizType.CHECKIN_RECORD
    )

    override suspend fun handle(entity: SyncOutboxEntity) {
        when (entity.bizType) {
            SyncOutboxBizType.STUDY_RECORD -> {
                val payload = gson.fromJson(entity.payload, StudyRecordSyncPayload::class.java)
                remoteUserSyncDataSource.appendStudyRecord(
                    date = payload.date,
                    wordId = payload.wordId,
                    word = payload.word,
                    definition = payload.definition,
                    isNewWord = payload.isNewWord
                ).getOrThrow()
            }

            SyncOutboxBizType.DAILY_STUDY_DURATION -> {
                val payload = gson.fromJson(entity.payload, DailyStudyDurationSyncPayload::class.java)
                remoteUserSyncDataSource.upsertDailyStudyDuration(
                    date = payload.date,
                    totalDurationMs = payload.totalDurationMs,
                    updatedAt = payload.updatedAt,
                    isNewPlanCompleted = payload.isNewPlanCompleted,
                    isReviewPlanCompleted = payload.isReviewPlanCompleted
                ).getOrThrow()
            }

            SyncOutboxBizType.FAVORITE -> {
                val payload = gson.fromJson(entity.payload, FavoriteSyncPayload::class.java)
                when (entity.operation) {
                    SyncOutboxOperation.UPSERT -> {
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

                    SyncOutboxOperation.DELETE -> {
                        remoteUserSyncDataSource.removeFavorite(payload.wordId).getOrThrow()
                    }
                }
            }

            SyncOutboxBizType.WORD_BOOK_PROGRESS -> {
                val payload = gson.fromJson(entity.payload, WordBookProgressSyncPayload::class.java)
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

            SyncOutboxBizType.WORD_STATE_UPSERT -> {
                val payload = gson.fromJson(entity.payload, WordStateUpsertSyncPayload::class.java)
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

            SyncOutboxBizType.WORD_STATE_DELETE_BY_BOOK -> {
                val payload = gson.fromJson(
                    entity.payload,
                    WordStateDeleteByBookSyncPayload::class.java
                )
                remoteUserSyncDataSource.deleteWordStatesByBookId(payload.bookId).getOrThrow()
            }

            SyncOutboxBizType.WORD_BOOK_SELECTION -> {
                val payload = gson.fromJson(entity.payload, WordBookSelectionSyncPayload::class.java)
                remoteUserSyncDataSource.setCurrentWordBookSelection(payload.bookId).getOrThrow()
            }

            SyncOutboxBizType.STUDY_PLAN -> {
                val payload = gson.fromJson(entity.payload, StudyPlanSyncPayload::class.java)
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

            SyncOutboxBizType.CHECKIN_RECORD -> {
                val payload = gson.fromJson(entity.payload, CheckInRecordSyncPayload::class.java)
                remoteUserSyncDataSource.upsertCheckInRecord(
                    date = payload.date,
                    type = payload.type,
                    signedAt = payload.signedAt,
                    updatedAt = payload.updatedAt
                ).getOrThrow()
            }
        }
    }

    override suspend fun onSuccess(entity: SyncOutboxEntity) {
        if (entity.bizType != SyncOutboxBizType.CHECKIN_RECORD) return
        runCatching {
            gson.fromJson(entity.payload, CheckInRecordSyncPayload::class.java)
        }.getOrNull()?.let { payload ->
            if (payload.type == "MAKEUP") {
                checkInConfigDataSource.consumeCachedMakeupCardBalance()
            }
        }
    }
}
