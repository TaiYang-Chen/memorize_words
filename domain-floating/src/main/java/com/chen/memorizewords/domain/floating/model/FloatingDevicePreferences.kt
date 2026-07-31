package com.chen.memorizewords.domain.floating.model

/** Device-local behaviour. These values must not be inferred from synced runtime state. */
data class FloatingDevicePreferences(
    val autoStartOnAppLaunch: Boolean = false,
    val floatingBallX: Int = 0,
    val floatingBallY: Int = 0,
    val dockConfig: FloatingDockConfig = FloatingDockConfig(),
    val dockState: FloatingDockState? = null
)
