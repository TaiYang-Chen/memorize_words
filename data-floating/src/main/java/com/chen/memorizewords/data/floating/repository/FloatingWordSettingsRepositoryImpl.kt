package com.chen.memorizewords.data.floating.repository

import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.data.floating.local.FloatingLegacyStorageKeys
import com.chen.memorizewords.data.sync.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.domain.floating.FloatingSettingsLocalStatePort
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
internal fun normalizeCharacterPackId(value: String?): String? =
    value?.trim()?.takeIf { it.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")) }
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
    return settings.copy(
        fieldConfigs = normalizeFieldConfigs(settings.fieldConfigs),
        ballSizePercent = normalizeBallSizePercent(settings.ballSizePercent),
        ballOpacityPercent = normalizeBallOpacityPercent(settings.ballOpacityPercent),
        cardOpacityPercent = normalizeCardOpacityPercent(settings.cardOpacityPercent),
        cardGapDp = normalizeCardGapDp(settings.cardGapDp),
        selectedCharacterPackId =
            normalizeCharacterPackId(settings.selectedCharacterPackId) ?: "green_pet"
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
        private const val KEY_SOURCE_TYPE = "floating_word_source_type"
        private const val KEY_ORDER_TYPE = "floating_word_order_type"
        private const val KEY_FIELD_CONFIGS = "floating_word_field_configs"
        private const val KEY_SELECTED_IDS = "floating_word_selected_ids"
        private const val KEY_BALL_SIZE_PERCENT = "floating_word_ball_size_percent"
        private const val KEY_BALL_OPACITY_PERCENT = "floating_word_ball_opacity_percent"
        private const val KEY_CARD_OPACITY_PERCENT = "floating_word_card_opacity_percent"
        private const val KEY_CARD_GAP_DP = "floating_word_card_gap_dp"
        private const val LEGACY_CHARACTER_PACK_ID = "green_pet"
        private const val KEY_SELECTED_CHARACTER_PACK_ID = "floating_word_selected_character_pack_id"
        private const val KEY_SETTINGS_PAYLOAD = FloatingLegacyStorageKeys.SETTINGS_PAYLOAD
    }

    private val fieldConfigType = object : TypeToken<List<FloatingWordFieldConfig>>() {}.type
    private val longListType = object : TypeToken<List<Long>>() {}.type
    private val localMonitor = Any()
    private val storageLockDepth = ThreadLocal<Int>()
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
        val changed = withStorageLock {
            val latest = readFromLocalLocked(migrateLegacy = true)
            if (latest == normalized) {
                state.value = latest
                false
            } else {
                persistSettingsLocked(normalized)
                state.value = normalized
                true
            }
        }
        if (changed) upload(normalized)
    }

    override suspend fun updateSettings(
        transform: (FloatingWordSettings) -> FloatingWordSettings
    ): FloatingWordSettings {
        var changed = false
        val updated = withStorageLock {
            val latest = readFromLocalLocked(migrateLegacy = true)
            val target = normalizeSettings(transform(latest))
            if (target != latest) {
                persistSettingsLocked(target)
                changed = true
            }
            state.value = target
            target
        }
        if (changed) upload(updated)
        return updated
    }

    override fun overwriteFromRemote(settings: FloatingWordSettings) {
        val normalized = normalizeSettings(settings)
        withStorageLock {
            persistSettingsLocked(normalized)
            state.value = normalized
        }
    }

    override fun clearLocalState() {
        withStorageLock {
            mmkv.removeValueForKey(KEY_SETTINGS_PAYLOAD)
            removeLegacyKeysLocked()
            state.value = FloatingWordSettings()
        }
    }

    private fun readFromLocal(): FloatingWordSettings = withStorageLock {
        readFromLocalLocked(migrateLegacy = true)
    }

    private fun readFromLocalLocked(migrateLegacy: Boolean): FloatingWordSettings {
        val payload = mmkv.decodeString(KEY_SETTINGS_PAYLOAD, null)
        if (!payload.isNullOrBlank()) {
            runCatching {
                normalizeSettings(gson.fromJson(payload, FloatingWordSettings::class.java))
            }.getOrNull()?.let { return it }
        }

        val hasLegacyState = legacyContentKeys().any(mmkv::containsKey)
        val legacy = readLegacySettingsLocked()
        if (migrateLegacy && (hasLegacyState || !payload.isNullOrEmpty())) {
            persistSettingsLocked(legacy)
        }
        return legacy
    }

    private fun readLegacySettingsLocked(): FloatingWordSettings {
        val sourceTypeName =
            mmkv.decodeString(KEY_SOURCE_TYPE, FloatingWordSourceType.CURRENT_BOOK.name)
        val orderTypeName =
            mmkv.decodeString(KEY_ORDER_TYPE, FloatingWordOrderType.RANDOM.name)
        val fieldConfigsJson = mmkv.decodeString(KEY_FIELD_CONFIGS, null)
        val selectedIdsJson = mmkv.decodeString(KEY_SELECTED_IDS, null)

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
        val legacyEnabled = mmkv.decodeBool(KEY_ENABLED, false)
        return normalizeFloatingWordSettings(
            FloatingWordSettings(
                sourceType = sourceType,
                orderType = orderType,
                fieldConfigs = fieldConfigs,
                selectedWordIds = selectedIds,
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
                selectedCharacterPackId = mmkv.decodeString(KEY_SELECTED_CHARACTER_PACK_ID)
                    ?: if (legacyEnabled) LEGACY_CHARACTER_PACK_ID else null
            )
        )
    }

    private fun normalizeSettings(settings: FloatingWordSettings): FloatingWordSettings {
        return normalizeFloatingWordSettings(settings)
    }

    private fun persistSettingsLocked(settings: FloatingWordSettings) {
        preserveLegacyDevicePayloadLocked()
        val payload = try {
            gson.toJson(settings)
        } catch (error: Exception) {
            throw IllegalStateException("Failed to serialize floating settings", error)
        }
        check(mmkv.encode(KEY_SETTINGS_PAYLOAD, payload)) {
            "Failed to persist floating settings"
        }
        removeLegacyKeysLocked()
    }

    private fun preserveLegacyDevicePayloadLocked() {
        val legacyPayload = mmkv.decodeString(KEY_SETTINGS_PAYLOAD, null) ?: return
        if (!FloatingLegacyStorageKeys.containsDevicePreferences(legacyPayload)) return
        check(
            mmkv.encode(FloatingLegacyStorageKeys.DEVICE_PREFERENCES_BACKUP, legacyPayload)
        ) {
            "Failed to preserve floating device preferences for migration"
        }
    }

    private fun removeLegacyKeysLocked() {
        legacyContentKeys().forEach(mmkv::removeValueForKey)
    }

    private fun legacyContentKeys(): List<String> = listOf(
        KEY_ENABLED,
        KEY_SOURCE_TYPE,
        KEY_ORDER_TYPE,
        KEY_FIELD_CONFIGS,
        KEY_SELECTED_IDS,
        KEY_BALL_SIZE_PERCENT,
        KEY_BALL_OPACITY_PERCENT,
        KEY_CARD_OPACITY_PERCENT,
        KEY_CARD_GAP_DP,
        KEY_SELECTED_CHARACTER_PACK_ID
    )

    private fun <T> withStorageLock(block: () -> T): T = synchronized(localMonitor) {
        val depth = storageLockDepth.get() ?: 0
        if (depth > 0) return@synchronized block()
        mmkv.lock()
        storageLockDepth.set(1)
        try {
            mmkv.checkContentChangedByOuterProcess()
            block()
        } finally {
            storageLockDepth.remove()
            mmkv.unlock()
        }
    }

    private fun upload(settings: FloatingWordSettings) {
        directSyncLauncher.launch(
            operation = "floating_settings",
            orderingKey = "floating_settings",
            request = { remoteLearningSyncDataSource.updateFloatingSettings(settings) }
        )
    }
}
