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
        refreshLocked()
    }

    override suspend fun update(
        transform: (FloatingDevicePreferences) -> FloatingDevicePreferences
    ): FloatingDevicePreferences = mutex.withLock {
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
        const val KEY_AUTO_START_ON_APP_LAUNCH = "auto_start_on_app_launch"
        const val KEY_BALL_X = "floating_ball_x"
        const val KEY_BALL_Y = "floating_ball_y"
        const val KEY_DOCK_CONFIG = "floating_dock_config"
        const val KEY_DOCK_STATE = "floating_dock_state"
    }
}
