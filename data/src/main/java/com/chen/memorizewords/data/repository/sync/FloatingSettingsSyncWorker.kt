package com.chen.memorizewords.data.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chen.memorizewords.domain.model.floating.FloatingDockConfig
import com.chen.memorizewords.domain.model.floating.FloatingDockState
import com.chen.memorizewords.domain.model.floating.FloatingWordFieldConfig
import com.chen.memorizewords.domain.model.floating.FloatingWordOrderType
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.domain.model.floating.FloatingWordSourceType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException

class FloatingSettingsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            LearningSyncWorkerEntryPoint::class.java
        )
        if (!entryPoint.authStateProvider().isAuthenticated()) {
            return Result.success()
        }

        val enabled = inputData.getBoolean(SyncWorkConstants.KEY_FLOATING_ENABLED, false)
        val sourceTypeName = inputData.getString(SyncWorkConstants.KEY_FLOATING_SOURCE_TYPE)
        val orderTypeName = inputData.getString(SyncWorkConstants.KEY_FLOATING_ORDER_TYPE)
        val fieldConfigsJson = inputData.getString(SyncWorkConstants.KEY_FLOATING_FIELD_CONFIGS)
        val selectedIdsJson = inputData.getString(SyncWorkConstants.KEY_FLOATING_SELECTED_WORD_IDS)
        val ballX = inputData.getInt(SyncWorkConstants.KEY_FLOATING_BALL_X, 0)
        val ballY = inputData.getInt(SyncWorkConstants.KEY_FLOATING_BALL_Y, 0)
        val autoStartOnBoot = inputData.getBoolean(
            SyncWorkConstants.KEY_FLOATING_AUTO_START_ON_BOOT,
            false
        )
        val autoStartOnAppLaunch = inputData.getBoolean(
            SyncWorkConstants.KEY_FLOATING_AUTO_START_ON_APP_LAUNCH,
            false
        )
        val cardOpacityPercent = inputData.getInt(
            SyncWorkConstants.KEY_FLOATING_CARD_OPACITY_PERCENT,
            100
        )
        val dockConfigJson = inputData.getString(SyncWorkConstants.KEY_FLOATING_DOCK_CONFIG)
        val dockStateJson = inputData.getString(SyncWorkConstants.KEY_FLOATING_DOCK_STATE)

        val sourceType = runCatching {
            FloatingWordSourceType.valueOf(sourceTypeName.orEmpty())
        }.getOrDefault(FloatingWordSourceType.CURRENT_BOOK)
        val orderType = runCatching {
            FloatingWordOrderType.valueOf(orderTypeName.orEmpty())
        }.getOrDefault(FloatingWordOrderType.RANDOM)

        val fieldConfigType = object : TypeToken<List<FloatingWordFieldConfig>>() {}.type
        val longListType = object : TypeToken<List<Long>>() {}.type
        val fieldConfigs = runCatching {
            gson.fromJson<List<FloatingWordFieldConfig>>(fieldConfigsJson, fieldConfigType)
        }.getOrNull() ?: FloatingWordSettings.defaultFieldConfigs()
        val selectedIds = runCatching {
            gson.fromJson<List<Long>>(selectedIdsJson, longListType)
        }.getOrNull() ?: emptyList()

        val settings = FloatingWordSettings(
            enabled = enabled,
            autoStartOnBoot = autoStartOnBoot,
            autoStartOnAppLaunch = autoStartOnAppLaunch,
            sourceType = sourceType,
            orderType = orderType,
            fieldConfigs = fieldConfigs,
            selectedWordIds = selectedIds,
            floatingBallX = ballX,
            floatingBallY = ballY,
            dockConfig = dockConfigJson?.let {
                runCatching { gson.fromJson(it, FloatingDockConfig::class.java) }.getOrNull()
            } ?: FloatingDockConfig(),
            cardOpacityPercent = cardOpacityPercent,
            dockState = dockStateJson?.let {
                runCatching { gson.fromJson(it, FloatingDockState::class.java) }.getOrNull()
            }
        )

        return try {
            entryPoint.remoteLearningSyncDataSource()
                .updateFloatingSettings(settings)
                .getOrThrow()
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
