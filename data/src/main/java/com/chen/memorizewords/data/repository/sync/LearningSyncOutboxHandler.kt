package com.chen.memorizewords.data.repository.sync

import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxEntity
import com.chen.memorizewords.data.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.domain.model.floating.FloatingDockConfig
import com.chen.memorizewords.domain.model.floating.FloatingDockState
import com.chen.memorizewords.domain.model.floating.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.model.floating.FloatingWordFieldConfig
import com.chen.memorizewords.domain.model.floating.FloatingWordOrderType
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.domain.model.floating.FloatingWordSourceType
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode
import com.chen.memorizewords.domain.model.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.model.practice.PracticeSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningSyncOutboxHandler @Inject constructor(
    private val remoteLearningSyncDataSource: RemoteLearningSyncDataSource,
    private val gson: Gson
) : SyncOutboxHandler {

    override val bizTypes: Set<String> = setOf(
        SyncOutboxBizType.PRACTICE_DURATION,
        SyncOutboxBizType.PRACTICE_SESSION,
        SyncOutboxBizType.PRACTICE_SETTINGS,
        SyncOutboxBizType.FLOATING_SETTINGS,
        SyncOutboxBizType.FLOATING_DISPLAY_RECORD
    )

    override suspend fun handle(entity: SyncOutboxEntity) {
        when (entity.bizType) {
            SyncOutboxBizType.PRACTICE_DURATION -> {
                val payload = gson.fromJson(entity.payload, PracticeDurationSyncPayload::class.java)
                remoteLearningSyncDataSource.upsertPracticeDuration(
                    date = payload.date,
                    totalDurationMs = payload.totalDurationMs,
                    updatedAt = payload.updatedAt
                ).getOrThrow()
            }

            SyncOutboxBizType.PRACTICE_SESSION -> {
                val payload = gson.fromJson(entity.payload, PracticeSessionSyncPayload::class.java)
                remoteLearningSyncDataSource.appendPracticeSession(
                    PracticeSessionRecord(
                        id = payload.id,
                        date = payload.date,
                        mode = runCatching { PracticeMode.valueOf(payload.mode) }
                            .getOrDefault(PracticeMode.LISTENING),
                        entryType = runCatching { PracticeEntryType.valueOf(payload.entryType) }
                            .getOrDefault(PracticeEntryType.RANDOM),
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

            SyncOutboxBizType.PRACTICE_SETTINGS -> {
                val payload = gson.fromJson(entity.payload, PracticeSettingsSyncPayload::class.java)
                remoteLearningSyncDataSource.updatePracticeSettings(
                    PracticeSettings(
                        selectedBookId = payload.selectedBookId,
                        intervalSeconds = payload.intervalSeconds,
                        loopEnabled = payload.loopEnabled,
                        playWordSpelling = payload.playWordSpelling,
                        playChineseMeaning = payload.playChineseMeaning
                    )
                ).getOrThrow()
            }

            SyncOutboxBizType.FLOATING_SETTINGS -> {
                val payload = gson.fromJson(entity.payload, FloatingSettingsSyncPayload::class.java)
                val fieldConfigType = object : TypeToken<List<FloatingWordFieldConfig>>() {}.type
                val selectedIdsType = object : TypeToken<List<Long>>() {}.type
                remoteLearningSyncDataSource.updateFloatingSettings(
                    FloatingWordSettings(
                        enabled = payload.enabled,
                        sourceType = runCatching { FloatingWordSourceType.valueOf(payload.sourceType) }
                            .getOrDefault(FloatingWordSourceType.CURRENT_BOOK),
                        orderType = runCatching { FloatingWordOrderType.valueOf(payload.orderType) }
                            .getOrDefault(FloatingWordOrderType.RANDOM),
                        fieldConfigs = gson.fromJson(payload.fieldConfigsJson, fieldConfigType) ?: emptyList(),
                        selectedWordIds = gson.fromJson(payload.selectedWordIdsJson, selectedIdsType)
                            ?: emptyList(),
                        floatingBallX = payload.floatingBallX,
                        floatingBallY = payload.floatingBallY,
                        autoStartOnBoot = payload.autoStartOnBoot,
                        autoStartOnAppLaunch = payload.autoStartOnAppLaunch,
                        cardOpacityPercent = payload.cardOpacityPercent,
                        dockConfig = payload.dockConfigJson?.let {
                            gson.fromJson(it, FloatingDockConfig::class.java)
                        } ?: FloatingDockConfig(),
                        dockState = payload.dockStateJson?.let {
                            gson.fromJson(it, FloatingDockState::class.java)
                        }
                    )
                ).getOrThrow()
            }

            SyncOutboxBizType.FLOATING_DISPLAY_RECORD -> {
                val payload = gson.fromJson(entity.payload, FloatingDisplayRecordSyncPayload::class.java)
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
