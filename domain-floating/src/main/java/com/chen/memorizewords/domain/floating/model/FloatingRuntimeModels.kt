package com.chen.memorizewords.domain.floating.model

/**
 * Local runtime lifecycle for the floating experience. It is deliberately separate from
 * syncable preferences and from character-pack download bookkeeping.
 */
enum class FloatingRuntimePhase {
    IDLE,
    RESOLVING,
    AWAITING_PERMISSION,
    AWAITING_CHARACTER,
    DOWNLOADING,
    INSTALLING,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED
}

enum class FloatingRuntimeSource {
    HOME,
    CHARACTER_SELECTION,
    APP_LAUNCH
}

enum class FloatingRuntimeError {
    PERMISSION_DENIED,
    MEMBERSHIP_REQUIRED,
    CHARACTER_UNAVAILABLE,
    DOWNLOAD_FAILED,
    FOREGROUND_SERVICE_REJECTED,
    RENDER_TIMEOUT,
    RENDER_FAILED,
    STOP_FAILED,
    UNKNOWN
}

data class FloatingRuntimeSession(
    val sessionId: String,
    val revision: Long,
    val phase: FloatingRuntimePhase,
    val source: FloatingRuntimeSource,
    val targetPackId: String? = null,
    val progress: Int = 0,
    val error: FloatingRuntimeError? = null,
    val startDeadlineAtMs: Long? = null,
    val lastHeartbeatAtMs: Long? = null,
    val configVersion: Long = 0L,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

data class FloatingRuntimeSnapshot(
    val session: FloatingRuntimeSession? = null
) {
    val phase: FloatingRuntimePhase get() = session?.phase ?: FloatingRuntimePhase.IDLE
    val isActive: Boolean get() = phase != FloatingRuntimePhase.IDLE && phase != FloatingRuntimePhase.FAILED
}

sealed interface FloatingRuntimeEvent {
    data class Resolved(val packId: String) : FloatingRuntimeEvent
    data object PermissionRequired : FloatingRuntimeEvent
    data object CharacterRequired : FloatingRuntimeEvent
    data class DownloadQueued(val progress: Int = 0) : FloatingRuntimeEvent
    data class DownloadProgress(val progress: Int) : FloatingRuntimeEvent
    data object Installing : FloatingRuntimeEvent
    /** The package transaction committed; Controller may now dispatch the renderer start. */
    data object InstallationReady : FloatingRuntimeEvent
    data class StartDispatched(val deadlineAtMs: Long) : FloatingRuntimeEvent
    data object RendererReady : FloatingRuntimeEvent
    data object StopRequested : FloatingRuntimeEvent
    data object Stopped : FloatingRuntimeEvent
    data class Failed(val error: FloatingRuntimeError) : FloatingRuntimeEvent
    data class Heartbeat(val atMs: Long) : FloatingRuntimeEvent
    data class Reconfigured(val configVersion: Long) : FloatingRuntimeEvent
}
