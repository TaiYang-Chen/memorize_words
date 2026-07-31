package com.chen.memorizewords.domain.floating.service

import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession

/** Android boundary for foreground-service dispatch. UI code must never call the service directly. */
interface FloatingRuntimeServiceGateway {
    fun canDrawOverlays(): Boolean
    fun dispatchStart(session: FloatingRuntimeSession): Result<Unit>
    fun dispatchStop(session: FloatingRuntimeSession?): Result<Unit>
    fun dispatchReconfigure(session: FloatingRuntimeSession): Result<Unit>
}
