package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.domain.floating.model.FloatingRuntimePhase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession

internal enum class FloatingStartCommandAction {
    START,
    IGNORE
}

/**
 * A start command is valid only for the current persisted STARTING revision. Commands delivered
 * after the renderer has advanced to RUNNING, or while that same revision is already starting,
 * must not tear down an active surface.
 */
internal fun resolveFloatingStartCommandAction(
    runtimeSession: FloatingRuntimeSession?,
    commandSessionId: String,
    commandRevision: Long,
    activeSessionId: String?,
    activeRevision: Long?,
    pendingSessionId: String?,
    pendingRevision: Long?
): FloatingStartCommandAction {
    val isCurrentStartingCommand = runtimeSession?.sessionId == commandSessionId &&
        runtimeSession.revision == commandRevision &&
        runtimeSession.phase == FloatingRuntimePhase.STARTING
    if (!isCurrentStartingCommand) return FloatingStartCommandAction.IGNORE

    val isAlreadyActive = activeSessionId == commandSessionId && activeRevision == commandRevision
    val isAlreadyPending = pendingSessionId == commandSessionId && pendingRevision == commandRevision
    return if (isAlreadyActive || isAlreadyPending) {
        FloatingStartCommandAction.IGNORE
    } else {
        FloatingStartCommandAction.START
    }
}
