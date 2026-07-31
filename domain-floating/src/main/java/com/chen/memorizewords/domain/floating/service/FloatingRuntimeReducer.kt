package com.chen.memorizewords.domain.floating.service

import com.chen.memorizewords.domain.floating.model.FloatingRuntimeEvent
import com.chen.memorizewords.domain.floating.model.FloatingRuntimePhase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession

/** Pure lifecycle transition policy shared by the app and the remote overlay service. */
object FloatingRuntimeReducer {
    fun reduce(
        current: FloatingRuntimeSession,
        event: FloatingRuntimeEvent,
        nowMs: Long
    ): FloatingRuntimeSession? {
        val next = when (event) {
            is FloatingRuntimeEvent.Resolved -> current.copy(
                phase = FloatingRuntimePhase.RESOLVING,
                targetPackId = event.packId,
                progress = 0,
                error = null,
                startDeadlineAtMs = null
            )
            FloatingRuntimeEvent.PermissionRequired -> current.copy(
                phase = FloatingRuntimePhase.AWAITING_PERMISSION,
                startDeadlineAtMs = null
            )
            FloatingRuntimeEvent.CharacterRequired -> current.copy(
                phase = FloatingRuntimePhase.AWAITING_CHARACTER,
                targetPackId = null,
                startDeadlineAtMs = null
            )
            is FloatingRuntimeEvent.DownloadQueued -> current.copy(
                phase = FloatingRuntimePhase.DOWNLOADING,
                progress = event.progress.coerceIn(0, 100),
                startDeadlineAtMs = null
            )
            is FloatingRuntimeEvent.DownloadProgress -> current.copy(
                phase = FloatingRuntimePhase.DOWNLOADING,
                progress = event.progress.coerceIn(0, 100),
                startDeadlineAtMs = null
            )
            FloatingRuntimeEvent.Installing -> current.copy(
                phase = FloatingRuntimePhase.INSTALLING,
                progress = 100,
                startDeadlineAtMs = null
            )
            FloatingRuntimeEvent.InstallationReady -> current.copy(
                phase = FloatingRuntimePhase.INSTALLING,
                progress = 100,
                startDeadlineAtMs = null
            )
            is FloatingRuntimeEvent.StartDispatched -> current.copy(
                phase = FloatingRuntimePhase.STARTING,
                progress = 100,
                error = null,
                startDeadlineAtMs = event.deadlineAtMs
            )
            FloatingRuntimeEvent.RendererReady -> current.copy(
                phase = FloatingRuntimePhase.RUNNING,
                error = null,
                startDeadlineAtMs = null,
                lastHeartbeatAtMs = nowMs
            )
            FloatingRuntimeEvent.StopRequested -> current.copy(
                phase = FloatingRuntimePhase.STOPPING,
                startDeadlineAtMs = null
            )
            FloatingRuntimeEvent.Stopped -> return null
            is FloatingRuntimeEvent.Failed -> current.copy(
                phase = FloatingRuntimePhase.FAILED,
                error = event.error,
                startDeadlineAtMs = null
            )
            is FloatingRuntimeEvent.Heartbeat -> current.copy(lastHeartbeatAtMs = event.atMs)
            is FloatingRuntimeEvent.Reconfigured -> current.copy(configVersion = event.configVersion)
        }
        return next.copy(revision = current.revision + 1, updatedAtMs = nowMs)
    }

    fun canHandle(phase: FloatingRuntimePhase, event: FloatingRuntimeEvent): Boolean {
        return when (event) {
            is FloatingRuntimeEvent.Resolved -> phase == FloatingRuntimePhase.RESOLVING
            FloatingRuntimeEvent.PermissionRequired,
            FloatingRuntimeEvent.CharacterRequired -> phase == FloatingRuntimePhase.RESOLVING
            is FloatingRuntimeEvent.DownloadQueued -> phase == FloatingRuntimePhase.RESOLVING
            is FloatingRuntimeEvent.DownloadProgress ->
                phase == FloatingRuntimePhase.DOWNLOADING
            FloatingRuntimeEvent.Installing -> phase == FloatingRuntimePhase.DOWNLOADING
            FloatingRuntimeEvent.InstallationReady -> phase == FloatingRuntimePhase.INSTALLING
            is FloatingRuntimeEvent.StartDispatched -> phase == FloatingRuntimePhase.RESOLVING ||
                phase == FloatingRuntimePhase.AWAITING_PERMISSION ||
                phase == FloatingRuntimePhase.DOWNLOADING ||
                phase == FloatingRuntimePhase.INSTALLING ||
                phase == FloatingRuntimePhase.STARTING
            FloatingRuntimeEvent.RendererReady -> phase == FloatingRuntimePhase.STARTING
            FloatingRuntimeEvent.StopRequested -> phase != FloatingRuntimePhase.IDLE &&
                phase != FloatingRuntimePhase.STOPPING
            FloatingRuntimeEvent.Stopped -> phase == FloatingRuntimePhase.STOPPING
            is FloatingRuntimeEvent.Failed -> phase != FloatingRuntimePhase.IDLE &&
                phase != FloatingRuntimePhase.FAILED
            is FloatingRuntimeEvent.Heartbeat -> phase == FloatingRuntimePhase.RUNNING
            is FloatingRuntimeEvent.Reconfigured -> phase == FloatingRuntimePhase.RUNNING
        }
    }
}
