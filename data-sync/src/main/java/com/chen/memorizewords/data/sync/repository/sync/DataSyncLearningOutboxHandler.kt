package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.domain.floating.model.FloatingDockConfig
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldConfig
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType
import com.chen.memorizewords.domain.practice.AudioLoopPlaybackMode
import com.chen.memorizewords.domain.practice.AudioLoopPlayOrder
import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.PracticeSettings
import com.chen.memorizewords.domain.sync.FloatingDisplayRecordSyncPayload
import com.chen.memorizewords.domain.sync.FloatingSettingsSyncPayload
import com.chen.memorizewords.domain.sync.OutboxRecord
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.PracticeDurationSyncPayload
import com.chen.memorizewords.domain.sync.PracticeSessionSyncPayload
import com.chen.memorizewords.domain.sync.PracticeSettingsSyncPayload
import com.chen.memorizewords.domain.sync.SyncOutboxHandler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncLearningOutboxHandler @Inject constructor(
    private val remoteLearningSyncDataSource: RemoteLearningSyncDataSource,
    private val gson: Gson
) : SyncOutboxHandler {

    override val topics: Set<String> = setOf(
        OutboxTopic.PRACTICE_DURATION,
        OutboxTopic.PRACTICE_SESSION,
        OutboxTopic.PRACTICE_SETTINGS,
        OutboxTopic.FLOATING_SETTINGS,
        OutboxTopic.FLOATING_DISPLAY_RECORD
    )

    override suspend fun handle(record: OutboxRecord) {
        when (record.aggregate) {
            OutboxTopic.PRACTICE_DURATION -> {
                val payload = gson.fromJson(record.payload, PracticeDurationSyncPayload::class.java)
                remoteLearningSyncDataSource.upsertPracticeDuration(
                    date = payload.date,
                    totalDurationMs = payload.totalDurationMs,
                    updatedAt = payload.updatedAt
                ).getOrThrow()
            }

            OutboxTopic.PRACTICE_SESSION -> {
                val payload = gson.fromJson(record.payload, PracticeSessionSyncPayload::class.java)
                remoteLearningSyncDataSource.appendPracticeSession(
                    PracticeSessionRecord(
                        id = payload.id,
                        date = payload.date,
                        mode = runCatching { PracticeMode.valueOf(payload.mode) }
                            .getOrDefault(PracticeMode.LISTENING),
                        entryType = runCatching { PracticeEntryType.valueOf(payload.entryType) }
                            .getOrDefault(PracticeEntryType.SELF),
                        entryCount = payload.entryCount,
                        durationMs = payload.durationMs,
                        createdAt = payload.createdAt,
                        wordIds = payload.wordIds,
                        questionCount = payload.questionCount,
                        completedCount = payload.completedCount,
                        correctCount = payload.correctCount,
                        submitCount = payload.submitCount
                    )
                ).getOrThrow()
            }

            OutboxTopic.PRACTICE_SETTINGS -> {
                val payload = gson.fromJson(record.payload, PracticeSettingsSyncPayload::class.java)
                remoteLearningSyncDataSource.updatePracticeSettings(
                    PracticeSettings(
                        selectedBookId = payload.selectedBookId,
                        intervalSeconds = payload.intervalSeconds,
                        loopEnabled = payload.loopEnabled,
                        showPhonetic = payload.showPhonetic,
                        showMeaning = payload.showMeaning,
                        playbackMode = runCatching {
                            AudioLoopPlaybackMode.valueOf(payload.playbackMode)
                        }.getOrDefault(AudioLoopPlaybackMode.WORD_ONLY),
                        playTimes = payload.playTimes.coerceAtLeast(1),
                        wordRepeatTimes = payload.wordRepeatTimes.coerceAtLeast(1),
                        exampleRepeatTimes = payload.exampleRepeatTimes.coerceAtLeast(1),
                        dictationPauseSeconds = payload.dictationPauseSeconds.coerceAtLeast(0),
                        revealDelaySeconds = payload.revealDelaySeconds.coerceAtLeast(0),
                        playbackSpeed = payload.playbackSpeed.coerceIn(0.5f, 2.0f),
                        timedStopMinutes = payload.timedStopMinutes.coerceAtLeast(0),
                        keepScreenOn = payload.keepScreenOn,
                        playOrder = runCatching {
                            AudioLoopPlayOrder.valueOf(payload.playOrder)
                        }.getOrDefault(AudioLoopPlayOrder.SEQUENTIAL)
                    )
                ).getOrThrow()
            }

            OutboxTopic.FLOATING_SETTINGS -> {
                val payload = gson.fromJson(record.payload, FloatingSettingsSyncPayload::class.java)
                val fieldConfigType = object : TypeToken<List<FloatingWordFieldConfig>>() {}.type
                val selectedIdsType = object : TypeToken<List<Long>>() {}.type
                remoteLearningSyncDataSource.updateFloatingSettings(
                    FloatingWordSettings(
                        enabled = payload.enabled,
                        sourceType = runCatching { FloatingWordSourceType.valueOf(payload.sourceType) }
                            .getOrDefault(FloatingWordSourceType.CURRENT_BOOK),
                        orderType = runCatching { FloatingWordOrderType.valueOf(payload.orderType) }
                            .getOrDefault(FloatingWordOrderType.RANDOM),
                        fieldConfigs = runCatching {
                            gson.fromJson<List<FloatingWordFieldConfig>>(
                                payload.fieldConfigsJson,
                                fieldConfigType
                            )
                        }.getOrNull().orEmpty(),
                        selectedWordIds = runCatching {
                            gson.fromJson<List<Long>>(payload.selectedWordIdsJson, selectedIdsType)
                        }.getOrNull().orEmpty(),
                        floatingBallX = payload.floatingBallX,
                        floatingBallY = payload.floatingBallY,
                        autoStartOnBoot = payload.autoStartOnBoot,
                        autoStartOnAppLaunch = payload.autoStartOnAppLaunch,
                        ballSizePercent = payload.ballSizePercent ?: 100,
                        ballOpacityPercent = payload.ballOpacityPercent,
                        cardOpacityPercent = payload.cardOpacityPercent,
                        cardGapDp = payload.cardGapDp,
                        dockConfig = payload.dockConfigJson?.let {
                            gson.fromJson(it, FloatingDockConfig::class.java)
                        } ?: FloatingDockConfig(),
                        dockState = payload.dockStateJson?.let {
                            gson.fromJson(it, FloatingDockState::class.java)
                        }
                    )
                ).getOrThrow()
            }

            OutboxTopic.FLOATING_DISPLAY_RECORD -> {
                val payload = gson.fromJson(record.payload, FloatingDisplayRecordSyncPayload::class.java)
                remoteLearningSyncDataSource.upsertFloatingDisplayRecord(
                    FloatingWordDisplayRecord(
                        date = payload.date,
                        displayCount = payload.displayCount,
                        wordIds = payload.wordIds,
                        updatedAt = payload.updatedAt
                    )
                ).getOrThrow()
            }
        }
    }
}
