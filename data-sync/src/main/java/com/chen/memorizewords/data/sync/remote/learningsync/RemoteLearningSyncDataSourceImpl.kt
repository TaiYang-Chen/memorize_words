package com.chen.memorizewords.data.sync.remote.learningsync

import com.chen.memorizewords.core.network.remote.RemoteResultAdapter
import com.chen.memorizewords.domain.floating.model.FloatingDockConfig
import com.chen.memorizewords.domain.floating.model.FloatingDockEdge
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldConfig
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldType
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType
import com.chen.memorizewords.domain.practice.AudioLoopPlaybackMode
import com.chen.memorizewords.domain.practice.AudioLoopPlayOrder
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.PracticeSettings
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingDisplayRecordDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingDisplayRecordSyncRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingDockConfigDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingDockStateDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingFieldConfigDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingSettingsDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingSettingsSyncRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningEventRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningEventResultDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningSyncRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeDurationDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeDurationSyncRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeSessionDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeSessionSyncRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeSettingsDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeSettingsSyncRequest
import com.chen.memorizewords.core.network.http.PageData
import javax.inject.Inject

private const val PRACTICE_SETTINGS_PROVIDER = "BAIDU"

class RemoteLearningSyncDataSourceImpl @Inject constructor(
    private val request: LearningSyncRequest,
    private val remoteResultAdapter: RemoteResultAdapter
) : RemoteLearningSyncDataSource {

    override suspend fun recordLearningEvent(request: LearningEventRequest): Result<LearningEventResultDto> {
        return remoteResultAdapter.toResult { this.request.recordLearningEvent(request) }
    }

    override suspend fun getPracticeSettings(): Result<PracticeSettings?> {
        return remoteResultAdapter.toResult { request.getPracticeSettings() }
            .map { it?.toDomain() }
    }

    override suspend fun updatePracticeSettings(settings: PracticeSettings): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.updatePracticeSettings(
                PracticeSettingsSyncRequest(
                    selectedBookId = settings.selectedBookId,
                    intervalSeconds = settings.intervalSeconds,
                    loopEnabled = settings.loopEnabled,
                    showPhonetic = settings.showPhonetic,
                    showMeaning = settings.showMeaning,
                    playbackMode = settings.playbackMode.name,
                    playTimes = settings.playTimes.coerceAtLeast(1),
                    wordRepeatTimes = settings.wordRepeatTimes.coerceAtLeast(1),
                    exampleRepeatTimes = settings.exampleRepeatTimes.coerceAtLeast(1),
                    dictationPauseSeconds = settings.dictationPauseSeconds.coerceAtLeast(0),
                    revealDelaySeconds = settings.revealDelaySeconds.coerceAtLeast(0),
                    playbackSpeed = settings.playbackSpeed.coerceIn(0.5f, 2.0f),
                    timedStopMinutes = settings.timedStopMinutes.coerceAtLeast(0),
                    keepScreenOn = settings.keepScreenOn,
                    playOrder = settings.playOrder.name,
                    provider = PRACTICE_SETTINGS_PROVIDER
                )
            )
        }
    }

    override suspend fun getPracticeDurations(
        page: Int,
        count: Int
    ): Result<PageData<PracticeDurationDto>> {
        return remoteResultAdapter.toResult { request.getPracticeDurations(page = page, count = count) }
    }

    override suspend fun upsertPracticeDuration(
        date: String,
        totalDurationMs: Long,
        updatedAtMs: Long
    ): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.upsertPracticeDuration(
                date,
                PracticeDurationSyncRequest(
                    totalDurationMs = totalDurationMs,
                    updatedAtMs = updatedAtMs
                )
            )
        }
    }

    override suspend fun appendPracticeSession(record: PracticeSessionRecord): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.appendPracticeSession(
                PracticeSessionSyncRequest(
                    date = record.date,
                    mode = record.mode.name,
                    entryType = record.entryType.name,
                    entryCount = record.entryCount,
                    durationMs = record.durationMs,
                    createdAtMs = record.createdAtMs,
                    wordIds = record.wordIds,
                    questionCount = record.questionCount,
                    completedCount = record.completedCount,
                    correctCount = record.correctCount,
                    submitCount = record.submitCount
                )
            )
        }
    }

    override suspend fun getPracticeSessions(
        page: Int,
        count: Int
    ): Result<PageData<PracticeSessionDto>> {
        return remoteResultAdapter.toResult { request.getPracticeSessions(page = page, count = count) }
    }

    override suspend fun getFloatingSettings(): Result<FloatingWordSettings?> {
        return remoteResultAdapter.toResult { request.getFloatingSettings() }
            .map { it?.toDomain() }
    }

    override suspend fun updateFloatingSettings(settings: FloatingWordSettings): Result<Unit> {
        val configs = settings.fieldConfigs.map {
            FloatingFieldConfigDto(
                type = it.type.name,
                enabled = it.enabled,
                fontSizeSp = it.fontSizeSp
            )
        }
        return remoteResultAdapter.toResult {
            request.updateFloatingSettings(
                FloatingSettingsSyncRequest(
                    enabled = settings.enabled,
                    sourceType = settings.sourceType.name,
                    orderType = settings.orderType.name,
                    fieldConfigs = configs,
                    selectedWordIds = settings.selectedWordIds,
                    floatingBallX = settings.floatingBallX,
                    floatingBallY = settings.floatingBallY,
                    autoStartOnBoot = settings.autoStartOnBoot,
                    autoStartOnAppLaunch = settings.autoStartOnAppLaunch,
                    ballSizePercent = settings.ballSizePercent,
                    ballOpacityPercent = settings.ballOpacityPercent,
                    cardOpacityPercent = settings.cardOpacityPercent,
                    cardGapDp = settings.cardGapDp,
                    dockConfig = settings.dockConfig.toDto(),
                    dockState = settings.dockState?.toDto()
                )
            )
        }
    }

    override suspend fun getFloatingDisplayRecords(
        page: Int,
        count: Int
    ): Result<PageData<FloatingDisplayRecordDto>> {
        return remoteResultAdapter.toResult { request.getFloatingDisplayRecords(page = page, count = count) }
    }

    override suspend fun upsertFloatingDisplayRecord(record: FloatingWordDisplayRecord): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.upsertFloatingDisplayRecord(
                record.date,
                FloatingDisplayRecordSyncRequest(
                    displayCount = record.displayCount,
                    wordIds = record.wordIds,
                    updatedAtMs = record.updatedAtMs
                )
            )
        }
    }
}

fun PracticeSettingsDto.toDomain(): PracticeSettings {
    return PracticeSettings(
        selectedBookId = selectedBookId,
        intervalSeconds = intervalSeconds,
        loopEnabled = loopEnabled,
        showPhonetic = showPhonetic,
        showMeaning = showMeaning,
        playbackMode = runCatching { AudioLoopPlaybackMode.valueOf(playbackMode) }
            .getOrDefault(AudioLoopPlaybackMode.WORD_ONLY),
        playTimes = playTimes.coerceAtLeast(1),
        wordRepeatTimes = wordRepeatTimes.coerceAtLeast(1),
        exampleRepeatTimes = exampleRepeatTimes.coerceAtLeast(1),
        dictationPauseSeconds = dictationPauseSeconds.coerceAtLeast(0),
        revealDelaySeconds = revealDelaySeconds.coerceAtLeast(0),
        playbackSpeed = playbackSpeed.coerceIn(0.5f, 2.0f),
        timedStopMinutes = timedStopMinutes.coerceAtLeast(0),
        keepScreenOn = keepScreenOn,
        playOrder = runCatching { AudioLoopPlayOrder.valueOf(playOrder) }
            .getOrDefault(AudioLoopPlayOrder.SEQUENTIAL)
    )
}

fun FloatingSettingsDto.toDomain(): FloatingWordSettings {
    return FloatingWordSettings(
        enabled = enabled,
        sourceType = runCatching { FloatingWordSourceType.valueOf(sourceType) }
            .getOrDefault(FloatingWordSourceType.CURRENT_BOOK),
        orderType = runCatching { FloatingWordOrderType.valueOf(orderType) }
            .getOrDefault(FloatingWordOrderType.RANDOM),
        fieldConfigs = fieldConfigs.mapNotNull { it.toDomainOrNull() }
            .ifEmpty { FloatingWordSettings.defaultFieldConfigs() },
        selectedWordIds = selectedWordIds,
        floatingBallX = floatingBallX,
        floatingBallY = floatingBallY,
        autoStartOnBoot = autoStartOnBoot,
        autoStartOnAppLaunch = autoStartOnAppLaunch,
        ballSizePercent = ballSizePercent,
        ballOpacityPercent = ballOpacityPercent,
        cardOpacityPercent = cardOpacityPercent,
        cardGapDp = cardGapDp,
        dockConfig = dockConfig?.toDomain() ?: FloatingDockConfig(),
        dockState = dockState?.toDomainOrNull()
    )
}

internal fun FloatingFieldConfigDto.toDomainOrNull(): FloatingWordFieldConfig? {
    val type = runCatching { FloatingWordFieldType.valueOf(type) }.getOrNull() ?: return null
    return FloatingWordFieldConfig(
        type = type,
        enabled = enabled,
        fontSizeSp = fontSizeSp
    )
}

internal fun FloatingDockConfigDto.toDomain(): FloatingDockConfig {
    return FloatingDockConfig(
        snapTriggerDistanceDp = snapTriggerDistanceDp,
        halfHiddenEnabled = halfHiddenEnabled,
        allowedEdges = allowedEdges.mapNotNull(::parseDockEdge),
        edgePriority = edgePriority.mapNotNull(::parseDockEdge),
        snapAnimationDurationMs = snapAnimationDurationMs,
        tapExpandsCardAfterUnsnap = tapExpandsCardAfterUnsnap,
        initialDockEdge = parseDockEdge(initialDockEdge) ?: FloatingDockEdge.RIGHT
    )
}

internal fun FloatingDockStateDto.toDomainOrNull(): FloatingDockState? {
    val edge = dockedEdge?.let(::parseDockEdge) ?: return null
    return FloatingDockState(
        dockedEdge = edge,
        crossAxisPercent = crossAxisPercent
    )
}

internal fun FloatingDockConfig.toDto(): FloatingDockConfigDto {
    return FloatingDockConfigDto(
        snapTriggerDistanceDp = snapTriggerDistanceDp,
        halfHiddenEnabled = halfHiddenEnabled,
        allowedEdges = allowedEdges.map { it.name },
        edgePriority = edgePriority.map { it.name },
        snapAnimationDurationMs = snapAnimationDurationMs,
        tapExpandsCardAfterUnsnap = tapExpandsCardAfterUnsnap,
        initialDockEdge = initialDockEdge.name
    )
}

internal fun FloatingDockState.toDto(): FloatingDockStateDto {
    return FloatingDockStateDto(
        dockedEdge = dockedEdge?.name,
        crossAxisPercent = crossAxisPercent
    )
}

private fun parseDockEdge(name: String): FloatingDockEdge? {
    return runCatching { FloatingDockEdge.valueOf(name) }.getOrNull()
}
