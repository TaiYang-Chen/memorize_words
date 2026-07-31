package com.chen.memorizewords.data.floating.local

import android.content.Context
import com.chen.memorizewords.domain.floating.model.FloatingDevicePreferences
import com.chen.memorizewords.domain.floating.model.FloatingDockConfig
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.repository.FloatingDevicePreferencesRepository
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class FloatingDevicePreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : FloatingDevicePreferencesRepository {
    private val mmkv = run {
        MMKV.initialize(context.applicationContext)
        checkNotNull(MMKV.mmkvWithID(STORAGE_ID, MMKV.MULTI_PROCESS_MODE))
    }
    private val mutex = Mutex()
    private val state = MutableStateFlow(read())

    override fun observe(): Flow<FloatingDevicePreferences> = state.asStateFlow()

    override suspend fun get(): FloatingDevicePreferences = mutex.withLock {
        migrateLegacyIfNeededLocked()
        refreshLocked()
    }

    override suspend fun update(
        transform: (FloatingDevicePreferences) -> FloatingDevicePreferences
    ): FloatingDevicePreferences = mutex.withLock {
        migrateLegacyIfNeededLocked()
        val current = refreshLocked()
        val updated = normalize(transform(current))
        if (updated != current) {
            persist(updated)
            state.value = updated
        }
        updated
    }

    override suspend fun clear() = mutex.withLock {
        mmkv.lock()
        try {
            mmkv.clearAll()
            state.value = FloatingDevicePreferences()
        } finally {
            mmkv.unlock()
        }
    }

    private suspend fun migrateLegacyIfNeededLocked() {
        mmkv.lock()
        try {
            mmkv.checkContentChangedByOuterProcess()
            if (mmkv.decodeBool(KEY_MIGRATED, false)) return
            val legacy = MMKV.defaultMMKV(MMKV.MULTI_PROCESS_MODE, null)
            val legacyPayload = listOf(
                legacy?.decodeString(FloatingLegacyStorageKeys.DEVICE_PREFERENCES_BACKUP, null),
                legacy?.decodeString(FloatingLegacyStorageKeys.SETTINGS_PAYLOAD, null)
            ).firstNotNullOfOrNull { payload ->
                payload?.let {
                    runCatching { gson.fromJson(it, LegacyFloatingSettingsPayload::class.java) }
                        .getOrNull()
                        ?.takeIf(LegacyFloatingSettingsPayload::hasDeviceValues)
                }
            }
            persist(
                FloatingDevicePreferences(
                    autoStartOnAppLaunch = legacyPayload?.autoStartOnAppLaunch
                        ?: legacy?.decodeBool(KEY_LEGACY_AUTO_START_ON_APP_LAUNCH, false)
                        ?: false,
                    floatingBallX = legacyPayload?.floatingBallX
                        ?: legacy?.decodeInt(KEY_LEGACY_BALL_X, 0)
                        ?: 0,
                    floatingBallY = legacyPayload?.floatingBallY
                        ?: legacy?.decodeInt(KEY_LEGACY_BALL_Y, 0)
                        ?: 0,
                    dockConfig = legacyPayload?.dockConfig
                        ?: legacy?.decodeString(KEY_LEGACY_DOCK_CONFIG, null)
                        ?.let { gson.fromJson(it, FloatingDockConfig::class.java) }
                        ?: FloatingDockConfig(),
                    dockState = legacyPayload?.dockState
                        ?: legacy?.decodeString(KEY_LEGACY_DOCK_STATE, null)
                        ?.let { gson.fromJson(it, FloatingDockState::class.java) }
                )
            )
            legacy?.let { legacyStore ->
                LEGACY_KEYS.forEach(legacyStore::removeValueForKey)
            }
            mmkv.encode(KEY_MIGRATED, true)
        } finally {
            mmkv.unlock()
        }
    }

    private fun refreshLocked(): FloatingDevicePreferences {
        mmkv.lock()
        return try {
            mmkv.checkContentChangedByOuterProcess()
            read().also { latest ->
                if (latest != state.value) state.value = latest
            }
        } finally {
            mmkv.unlock()
        }
    }

    private fun read(): FloatingDevicePreferences = normalize(
        FloatingDevicePreferences(
            autoStartOnAppLaunch = mmkv.decodeBool(KEY_AUTO_START_ON_APP_LAUNCH, false),
            floatingBallX = mmkv.decodeInt(KEY_BALL_X, 0),
            floatingBallY = mmkv.decodeInt(KEY_BALL_Y, 0),
            dockConfig = mmkv.decodeString(KEY_DOCK_CONFIG, null)
                ?.let { payload ->
                    runCatching { gson.fromJson(payload, FloatingDockConfig::class.java) }
                        .getOrNull()
                }
                ?: FloatingDockConfig(),
            dockState = mmkv.decodeString(KEY_DOCK_STATE, null)
                ?.let { payload ->
                    runCatching { gson.fromJson(payload, FloatingDockState::class.java) }
                        .getOrNull()
                }
        )
    )

    private fun normalize(preferences: FloatingDevicePreferences): FloatingDevicePreferences {
        val dockConfig = preferences.dockConfig.normalized()
        return preferences.copy(
            dockConfig = dockConfig,
            dockState = preferences.dockState?.normalized(dockConfig)
        )
    }

    private fun persist(preferences: FloatingDevicePreferences) {
        val normalized = normalize(preferences)
        check(mmkv.encode(KEY_AUTO_START_ON_APP_LAUNCH, normalized.autoStartOnAppLaunch)) {
            "Failed to persist floating device preferences"
        }
        check(mmkv.encode(KEY_BALL_X, normalized.floatingBallX)) {
            "Failed to persist floating ball x"
        }
        check(mmkv.encode(KEY_BALL_Y, normalized.floatingBallY)) {
            "Failed to persist floating ball y"
        }
        check(mmkv.encode(KEY_DOCK_CONFIG, gson.toJson(normalized.dockConfig))) {
            "Failed to persist floating dock config"
        }
        if (normalized.dockState == null) {
            mmkv.removeValueForKey(KEY_DOCK_STATE)
        } else {
            check(mmkv.encode(KEY_DOCK_STATE, gson.toJson(normalized.dockState))) {
                "Failed to persist floating dock state"
            }
        }
    }

    private companion object {
        const val STORAGE_ID = "floating_device_preferences_v2"
        const val KEY_MIGRATED = "migrated_from_floating_settings_v1"
        const val KEY_AUTO_START_ON_APP_LAUNCH = "auto_start_on_app_launch"
        const val KEY_BALL_X = "floating_ball_x"
        const val KEY_BALL_Y = "floating_ball_y"
        const val KEY_DOCK_CONFIG = "floating_dock_config"
        const val KEY_DOCK_STATE = "floating_dock_state"
        const val KEY_LEGACY_AUTO_START_ON_APP_LAUNCH = "floating_word_auto_start_on_app_launch"
        const val KEY_LEGACY_BALL_X = "floating_word_ball_x"
        const val KEY_LEGACY_BALL_Y = "floating_word_ball_y"
        const val KEY_LEGACY_DOCK_CONFIG = "floating_word_dock_config"
        const val KEY_LEGACY_DOCK_STATE = "floating_word_dock_state"
        val LEGACY_KEYS = listOf(
            KEY_LEGACY_AUTO_START_ON_APP_LAUNCH,
            KEY_LEGACY_BALL_X,
            KEY_LEGACY_BALL_Y,
            KEY_LEGACY_DOCK_CONFIG,
            KEY_LEGACY_DOCK_STATE,
            FloatingLegacyStorageKeys.DEVICE_PREFERENCES_BACKUP,
            KEY_LEGACY_PENDING_PAYLOAD,
            KEY_LEGACY_PENDING_REQUEST_ID,
            KEY_LEGACY_PENDING_TARGET_PACK_ID,
            KEY_LEGACY_PENDING_SOURCE,
            KEY_LEGACY_PENDING_CREATED_AT
        )
        const val KEY_LEGACY_PENDING_PAYLOAD = "floating_activation_payload_v2"
        const val KEY_LEGACY_PENDING_REQUEST_ID = "floating_activation_request_id"
        const val KEY_LEGACY_PENDING_TARGET_PACK_ID = "floating_activation_target_pack_id"
        const val KEY_LEGACY_PENDING_SOURCE = "floating_activation_source"
        const val KEY_LEGACY_PENDING_CREATED_AT = "floating_activation_created_at"
    }

    private data class LegacyFloatingSettingsPayload(
        val autoStartOnAppLaunch: Boolean? = null,
        val floatingBallX: Int? = null,
        val floatingBallY: Int? = null,
        val dockConfig: FloatingDockConfig? = null,
        val dockState: FloatingDockState? = null
    ) {
        fun hasDeviceValues(): Boolean =
            autoStartOnAppLaunch != null ||
                floatingBallX != null ||
                floatingBallY != null ||
                dockConfig != null ||
                dockState != null
    }
}
