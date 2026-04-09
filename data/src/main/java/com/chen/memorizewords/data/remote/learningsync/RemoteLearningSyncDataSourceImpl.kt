package com.chen.memorizewords.data.remote.learningsync

import com.chen.memorizewords.data.remote.RemoteResultAdapter
import com.chen.memorizewords.domain.model.floating.FloatingDockConfig
import com.chen.memorizewords.domain.model.floating.FloatingDockEdge
import com.chen.memorizewords.domain.model.floating.FloatingDockState
import com.chen.memorizewords.domain.model.floating.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.model.floating.FloatingWordFieldConfig
import com.chen.memorizewords.domain.model.floating.FloatingWordFieldType
import com.chen.memorizewords.domain.model.floating.FloatingWordOrderType
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.domain.model.floating.FloatingWordSourceType
import com.chen.memorizewords.domain.model.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.model.practice.PracticeSettings
import com.chen.memorizewords.network.api.learningsync.FloatingDisplayRecordDto
import com.chen.memorizewords.network.api.learningsync.FloatingDisplayRecordSyncRequest
import com.chen.memorizewords.network.api.learningsync.FloatingDockConfigDto
import com.chen.memorizewords.network.api.learningsync.FloatingDockStateDto
import com.chen.memorizewords.network.api.learningsync.FloatingFieldConfigDto
import com.chen.memorizewords.network.api.learningsync.FloatingSettingsDto
import com.chen.memorizewords.network.api.learningsync.FloatingSettingsSyncRequest
import com.chen.memorizewords.network.api.learningsync.LearningSyncRequest
import com.chen.memorizewords.network.api.learningsync.PracticeDurationDto
import com.chen.memorizewords.network.api.learningsync.PracticeDurationSyncRequest
import com.chen.memorizewords.network.api.learningsync.PracticeSessionDto
import com.chen.memorizewords.network.api.learningsync.PracticeSessionSyncRequest
import com.chen.memorizewords.network.api.learningsync.PracticeSettingsDto
import com.chen.memorizewords.network.api.learningsync.PracticeSettingsSyncRequest
import com.chen.memorizewords.network.model.PageData
import javax.inject.Inject

private const val LEGACY_BACKEND_DEFAULT_PROVIDER = "BACKEND_DEFAULT"

class RemoteLearningSyncDataSourceImpl @Inject constructor(
    private val request: LearningSyncRequest,
    private val remoteResultAdapter: RemoteResultAdapter
) : RemoteLearningSyncDataSource {

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
                    playWordSpelling = settings.playWordSpelling,
                    playChineseMeaning = settings.playChineseMeaning,
                    provider = LEGACY_BACKEND_DEFAULT_PROVIDER
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
        updatedAt: Long
    ): Result<Unit> {
        return remoteResultAdapter.toResult {
            request.upsertPracticeDuration(
                date,
                PracticeDurationSyncRequest(
                    totalDurationMs = totalDurationMs,
                    updatedAt = updatedAt
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
                    createdAt = record.createdAt,
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
                    cardOpacityPercent = settings.cardOpacityPercent,
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
                    updatedAt = record.updatedAt
                )
            )
        }
    }
}

internal fun PracticeSettingsDto.toDomain(): PracticeSettings {
    return PracticeSettings(
        selectedBookId = selectedBookId,
        intervalSeconds = intervalSeconds,
        loopEnabled = loopEnabled,
        playWordSpelling = playWordSpelling,
        playChineseMeaning = playChineseMeaning
    )
}

internal fun FloatingSettingsDto.toDomain(): FloatingWordSettings {
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
        cardOpacityPercent = cardOpacityPercent,
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
