package com.chen.memorizewords.data.floating.repository

import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.data.sync.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.domain.floating.FloatingSettingsLocalStatePort
import com.chen.memorizewords.domain.floating.model.FloatingDockConfig
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldConfig
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val DEFAULT_BALL_OPACITY_PERCENT = 100
internal const val DEFAULT_CARD_OPACITY_PERCENT = 100
internal const val DEFAULT_BALL_SIZE_PERCENT = 60
internal const val DEFAULT_CARD_GAP_DP = -20
internal const val MIN_BALL_SIZE_PERCENT = 1
internal const val MAX_BALL_SIZE_PERCENT = 200
internal const val MIN_CARD_GAP_DP = -100
internal const val MAX_CARD_GAP_DP = 100

internal fun normalizeBallSizePercent(value: Int): Int =
    value.coerceIn(MIN_BALL_SIZE_PERCENT, MAX_BALL_SIZE_PERCENT)
internal fun normalizeBallOpacityPercent(value: Int): Int = value.coerceIn(0, 100)
internal fun normalizeCardOpacityPercent(value: Int): Int = value.coerceIn(0, 100)
internal fun normalizeCardGapDp(value: Int): Int =
    value.coerceIn(MIN_CARD_GAP_DP, MAX_CARD_GAP_DP)
internal fun normalizeCharacterPackId(value: String): String =
    value.takeIf { it.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")) }
        ?: FloatingWordSettings.DEFAULT_CHARACTER_PACK_ID
internal fun sanitizeDockState(
    dockState: FloatingDockState?,
    dockConfig: FloatingDockConfig
): FloatingDockState? = dockState?.normalized(dockConfig)

internal fun normalizeFieldConfigs(
    configs: List<FloatingWordFieldConfig>
): List<FloatingWordFieldConfig> {
    val defaults = FloatingWordSettings.defaultFieldConfigs()
    if (configs.isEmpty()) return defaults
    val existing = configs.map { it.copy(fontSizeSp = it.fontSizeSp.coerceAtLeast(8)) }
    val existingTypes = existing.map { it.type }.toSet()
    val missing = defaults.filter { it.type !in existingTypes }
    return existing + missing
}

internal fun normalizeFloatingWordSettings(settings: FloatingWordSettings): FloatingWordSettings {
    val normalizedDockConfig = settings.dockConfig.normalized()
    return settings.copy(
        fieldConfigs = normalizeFieldConfigs(settings.fieldConfigs),
        ballSizePercent = normalizeBallSizePercent(settings.ballSizePercent),
        ballOpacityPercent = normalizeBallOpacityPercent(settings.ballOpacityPercent),
        cardOpacityPercent = normalizeCardOpacityPercent(settings.cardOpacityPercent),
        cardGapDp = normalizeCardGapDp(settings.cardGapDp),
        selectedCharacterPackId = normalizeCharacterPackId(settings.selectedCharacterPackId),
        dockConfig = normalizedDockConfig,
        dockState = sanitizeDockState(settings.dockState, normalizedDockConfig)
    )
}

@Singleton
class FloatingWordSettingsRepositoryImpl @Inject constructor(
    private val mmkv: MMKV,
    private val gson: Gson,
    private val remoteLearningSyncDataSource: RemoteLearningSyncDataSource,
    private val directSyncLauncher: DirectSyncLauncher
) : FloatingWordSettingsRepository, FloatingSettingsLocalStatePort {

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
        private const val KEY_BALL_SIZE_PERCENT = "floating_word_ball_size_percent"
        private const val KEY_BALL_OPACITY_PERCENT = "floating_word_ball_opacity_percent"
        private const val KEY_CARD_OPACITY_PERCENT = "floating_word_card_opacity_percent"
        private const val KEY_CARD_GAP_DP = "floating_word_card_gap_dp"
        private const val KEY_SELECTED_CHARACTER_PACK_ID = "floating_word_selected_character_pack_id"
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
        upload(normalized)
    }

    override suspend fun updateBallPosition(x: Int, y: Int, dockState: FloatingDockState?) {
        val latest = normalizeSettings(readFromLocal().copy(
            floatingBallX = x,
            floatingBallY = y,
            dockState = dockState
        ))
        persistSettings(latest)
        state.value = latest
        upload(latest)
    }

    override fun overwriteFromRemote(settings: FloatingWordSettings) {
        val normalized = normalizeSettings(settings)
        persistSettings(normalized)
        state.value = normalized
    }

    override fun clearLocalState() {
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
            KEY_BALL_SIZE_PERCENT,
            KEY_BALL_OPACITY_PERCENT,
            KEY_CARD_OPACITY_PERCENT,
            KEY_CARD_GAP_DP,
            KEY_SELECTED_CHARACTER_PACK_ID
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

        val settings = normalizeFloatingWordSettings(
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
                ballSizePercent = mmkv.decodeInt(
                    KEY_BALL_SIZE_PERCENT,
                    DEFAULT_BALL_SIZE_PERCENT
                ),
                ballOpacityPercent = mmkv.decodeInt(
                    KEY_BALL_OPACITY_PERCENT,
                    DEFAULT_BALL_OPACITY_PERCENT
                ),
                cardOpacityPercent = mmkv.decodeInt(
                    KEY_CARD_OPACITY_PERCENT,
                    DEFAULT_CARD_OPACITY_PERCENT
                ),
                cardGapDp = mmkv.decodeInt(
                    KEY_CARD_GAP_DP,
                    DEFAULT_CARD_GAP_DP
                ),
                selectedCharacterPackId = mmkv.decodeString(
                    KEY_SELECTED_CHARACTER_PACK_ID,
                    FloatingWordSettings.DEFAULT_CHARACTER_PACK_ID
                ).orEmpty().ifBlank { FloatingWordSettings.DEFAULT_CHARACTER_PACK_ID },
                dockConfig = dockConfig,
                dockState = dockState
            )
        )
        if (dockStateJson != null) {
            persistSettings(settings, normalize = false)
        }
        return settings
    }

    private fun normalizeSettings(settings: FloatingWordSettings): FloatingWordSettings {
        return normalizeFloatingWordSettings(settings)
    }

    private fun persistSettings(
        settings: FloatingWordSettings,
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
        mmkv.encode(KEY_BALL_SIZE_PERCENT, target.ballSizePercent)
        mmkv.encode(KEY_BALL_OPACITY_PERCENT, target.ballOpacityPercent)
        mmkv.encode(KEY_CARD_OPACITY_PERCENT, target.cardOpacityPercent)
        mmkv.encode(KEY_CARD_GAP_DP, target.cardGapDp)
        mmkv.encode(KEY_SELECTED_CHARACTER_PACK_ID, target.selectedCharacterPackId)
        mmkv.encode(KEY_DOCK_CONFIG, gson.toJson(target.dockConfig))
        mmkv.encode(KEY_DOCK_STATE, target.dockState?.let(gson::toJson))
    }

    private fun upload(settings: FloatingWordSettings) {
        directSyncLauncher.launch(
            operation = "floating_settings",
            orderingKey = "floating_settings",
            request = { remoteLearningSyncDataSource.updateFloatingSettings(settings) }
        )
    }
}
