package com.chen.memorizewords.data.repository.floating

import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.repository.sync.FloatingSettingsSyncPayload
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.floating.FloatingDockConfig
import com.chen.memorizewords.domain.model.floating.FloatingDockState
import com.chen.memorizewords.domain.model.floating.FloatingWordFieldConfig
import com.chen.memorizewords.domain.model.floating.FloatingWordOrderType
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.domain.model.floating.FloatingWordSourceType
import com.chen.memorizewords.domain.repository.floating.FloatingWordSettingsRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val DEFAULT_CARD_OPACITY_PERCENT = 100

internal fun normalizeCardOpacityPercent(value: Int): Int = value.coerceIn(0, 100)

@Singleton
class FloatingWordSettingsRepositoryImpl @Inject constructor(
    private val mmkv: MMKV,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : FloatingWordSettingsRepository {

    companion object {
        private const val KEY_ENABLED = "floating_word_enabled"
        private const val KEY_AUTO_START_ON_BOOT = "floating_word_auto_start_on_boot"
        private const val KEY_AUTO_START_ON_APP_LAUNCH = "floating_word_auto_start_on_app_launch"
        private const val KEY_SOURCE_TYPE = "floating_word_source_type"
        private const val KEY_ORDER_TYPE = "floating_word_order_type"
        private const val KEY_FIELD_CONFIGS = "floating_word_field_configs"
        private const val KEY_SELECTED_IDS = "floating_word_selected_ids"
        private const val KEY_BALL_X = "floating_word_ball_x"
        private const val KEY_BALL_Y = "floating_word_ball_y"
        private const val KEY_DOCK_CONFIG = "floating_word_dock_config"
        private const val KEY_DOCK_STATE = "floating_word_dock_state"
        private const val KEY_CARD_OPACITY_PERCENT = "floating_word_card_opacity_percent"
        private const val KEY_SIZE_MIGRATED = "floating_word_size_migrated"
    }

    private val fieldConfigType = object : TypeToken<List<FloatingWordFieldConfig>>() {}.type
    private val longListType = object : TypeToken<List<Long>>() {}.type
    private val state = MutableStateFlow(readFromLocal())

    override fun observeSettings(): Flow<FloatingWordSettings> = state.asStateFlow()

    override suspend fun getSettings(): FloatingWordSettings {
        val latest = readFromLocal()
        if (latest != state.value) {
            state.value = latest
        }
        return state.value
    }

    override suspend fun saveSettings(settings: FloatingWordSettings) {
        val normalized = normalizeSettings(settings)
        persistSettings(normalized)
        state.value = normalized
        persistOutbox(normalized)
    }

    override suspend fun updateBallPosition(x: Int, y: Int, dockState: FloatingDockState?) {
        val latest = normalizeSettings(readFromLocal().copy(
            floatingBallX = x,
            floatingBallY = y,
            dockState = dockState
        ))
        persistSettings(latest)
        state.value = latest
        persistOutbox(latest)
    }

    fun overwriteFromRemote(settings: FloatingWordSettings) {
        val normalized = normalizeSettings(settings)
        persistSettings(normalized)
        state.value = normalized
    }

    fun clearLocalState() {
        listOf(
            KEY_ENABLED,
            KEY_AUTO_START_ON_BOOT,
            KEY_AUTO_START_ON_APP_LAUNCH,
            KEY_SOURCE_TYPE,
            KEY_ORDER_TYPE,
            KEY_FIELD_CONFIGS,
            KEY_SELECTED_IDS,
            KEY_BALL_X,
            KEY_BALL_Y,
            KEY_DOCK_CONFIG,
            KEY_DOCK_STATE,
            KEY_CARD_OPACITY_PERCENT,
            KEY_SIZE_MIGRATED
        ).forEach(mmkv::removeValueForKey)
        state.value = readFromLocal()
    }

    private fun readFromLocal(): FloatingWordSettings {
        val sourceTypeName =
            mmkv.decodeString(KEY_SOURCE_TYPE, FloatingWordSourceType.CURRENT_BOOK.name)
        val orderTypeName =
            mmkv.decodeString(KEY_ORDER_TYPE, FloatingWordOrderType.RANDOM.name)
        val fieldConfigsJson = mmkv.decodeString(KEY_FIELD_CONFIGS, null)
        val selectedIdsJson = mmkv.decodeString(KEY_SELECTED_IDS, null)
        val dockConfigJson = mmkv.decodeString(KEY_DOCK_CONFIG, null)
        val dockStateJson = mmkv.decodeString(KEY_DOCK_STATE, null)

        val sourceType = runCatching {
            FloatingWordSourceType.valueOf(sourceTypeName.orEmpty())
        }.getOrDefault(FloatingWordSourceType.CURRENT_BOOK)
        val orderType = runCatching {
            FloatingWordOrderType.valueOf(orderTypeName.orEmpty())
        }.getOrDefault(FloatingWordOrderType.RANDOM)
        val fieldConfigs = runCatching {
            gson.fromJson<List<FloatingWordFieldConfig>>(fieldConfigsJson, fieldConfigType)
        }.getOrNull() ?: FloatingWordSettings.defaultFieldConfigs()
        val selectedIds = runCatching {
            gson.fromJson<List<Long>>(selectedIdsJson, longListType)
        }.getOrNull() ?: emptyList()
        val dockConfig = runCatching {
            gson.fromJson(dockConfigJson, FloatingDockConfig::class.java)
        }.getOrNull() ?: FloatingDockConfig()
        val dockState = runCatching {
            gson.fromJson(dockStateJson, FloatingDockState::class.java)
        }.getOrNull()

        val settings = normalizeSettings(
            FloatingWordSettings(
                enabled = mmkv.decodeBool(KEY_ENABLED, false),
                autoStartOnBoot = mmkv.decodeBool(KEY_AUTO_START_ON_BOOT, false),
                autoStartOnAppLaunch = mmkv.decodeBool(KEY_AUTO_START_ON_APP_LAUNCH, false),
                sourceType = sourceType,
                orderType = orderType,
                fieldConfigs = fieldConfigs,
                selectedWordIds = selectedIds,
                floatingBallX = mmkv.decodeInt(KEY_BALL_X, 0),
                floatingBallY = mmkv.decodeInt(KEY_BALL_Y, 0),
                cardOpacityPercent = mmkv.decodeInt(
                    KEY_CARD_OPACITY_PERCENT,
                    DEFAULT_CARD_OPACITY_PERCENT
                ),
                dockConfig = dockConfig,
                dockState = dockState
            )
        )
        return maybeMigrateSizes(settings)
    }

    private fun normalizeSettings(settings: FloatingWordSettings): FloatingWordSettings {
        val normalizedDockConfig = settings.dockConfig.normalized()
        return settings.copy(
            fieldConfigs = normalizeFieldConfigs(settings.fieldConfigs),
            cardOpacityPercent = normalizeCardOpacityPercent(settings.cardOpacityPercent),
            dockConfig = normalizedDockConfig,
            dockState = settings.dockState?.normalized(normalizedDockConfig)
        )
    }

    private fun normalizeFieldConfigs(
        configs: List<FloatingWordFieldConfig>
    ): List<FloatingWordFieldConfig> {
        val defaults = FloatingWordSettings.defaultFieldConfigs()
        if (configs.isEmpty()) return defaults
        val existing = configs.map { it.copy(fontSizeSp = it.fontSizeSp.coerceAtLeast(8)) }
        val existingTypes = existing.map { it.type }.toSet()
        val missing = defaults.filter { it.type !in existingTypes }
        return existing + missing
    }

    private fun maybeMigrateSizes(settings: FloatingWordSettings): FloatingWordSettings {
        if (mmkv.decodeBool(KEY_SIZE_MIGRATED, false)) return settings
        val defaults = FloatingWordSettings.defaultFieldConfigs()
        val defaultMap = defaults.associateBy { it.type }
        val sourceConfigs = if (settings.fieldConfigs.isEmpty()) defaults else settings.fieldConfigs
        val migratedConfigs = sourceConfigs.map { config ->
            val default = defaultMap[config.type]
            if (default != null) {
                config.copy(fontSizeSp = default.fontSizeSp)
            } else {
                config
            }
        }
        val migrated = normalizeSettings(settings.copy(fieldConfigs = migratedConfigs))
        persistSettings(migrated, markMigrated = true, normalize = false)
        return migrated
    }

    private fun persistSettings(
        settings: FloatingWordSettings,
        markMigrated: Boolean = false,
        normalize: Boolean = false
    ) {
        val target = if (normalize) {
            normalizeSettings(settings)
        } else {
            settings
        }
        mmkv.encode(KEY_ENABLED, target.enabled)
        mmkv.encode(KEY_AUTO_START_ON_BOOT, target.autoStartOnBoot)
        mmkv.encode(KEY_AUTO_START_ON_APP_LAUNCH, target.autoStartOnAppLaunch)
        mmkv.encode(KEY_SOURCE_TYPE, target.sourceType.name)
        mmkv.encode(KEY_ORDER_TYPE, target.orderType.name)
        mmkv.encode(KEY_FIELD_CONFIGS, gson.toJson(target.fieldConfigs))
        mmkv.encode(KEY_SELECTED_IDS, gson.toJson(target.selectedWordIds))
        mmkv.encode(KEY_BALL_X, target.floatingBallX)
        mmkv.encode(KEY_BALL_Y, target.floatingBallY)
        mmkv.encode(KEY_CARD_OPACITY_PERCENT, target.cardOpacityPercent)
        mmkv.encode(KEY_DOCK_CONFIG, gson.toJson(target.dockConfig))
        mmkv.encode(KEY_DOCK_STATE, target.dockState?.let(gson::toJson))
        if (markMigrated) {
            mmkv.encode(KEY_SIZE_MIGRATED, true)
        }
    }

    private suspend fun persistOutbox(settings: FloatingWordSettings) {
        syncOutboxDao.upsert(
            syncOutboxEntity(
                bizType = SyncOutboxBizType.FLOATING_SETTINGS,
                bizKey = "floating_settings",
                operation = SyncOutboxOperation.UPSERT,
                payload = gson.toJson(
                    FloatingSettingsSyncPayload(
                        enabled = settings.enabled,
                        sourceType = settings.sourceType.name,
                        orderType = settings.orderType.name,
                        fieldConfigsJson = gson.toJson(settings.fieldConfigs),
                        selectedWordIdsJson = gson.toJson(settings.selectedWordIds),
                        floatingBallX = settings.floatingBallX,
                        floatingBallY = settings.floatingBallY,
                        autoStartOnBoot = settings.autoStartOnBoot,
                        autoStartOnAppLaunch = settings.autoStartOnAppLaunch,
                        cardOpacityPercent = settings.cardOpacityPercent,
                        dockConfigJson = gson.toJson(settings.dockConfig),
                        dockStateJson = settings.dockState?.let(gson::toJson)
                    )
                )
            )
        )
        syncOutboxWorkScheduler.scheduleDrain()
    }
}
