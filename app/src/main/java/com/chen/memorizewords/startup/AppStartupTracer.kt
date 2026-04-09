package com.chen.memorizewords.startup

import android.os.SystemClock
import android.os.Trace
import android.util.Log

class AppStartupTracer internal constructor(
    private val nowMs: () -> Long,
    private val beginSection: (String) -> Unit,
    private val endSection: () -> Unit,
    private val logger: (String, String) -> Unit
) {
    constructor() : this(
        nowMs = { SystemClock.elapsedRealtime() },
        beginSection = { sectionName -> Trace.beginSection(sectionName) },
        endSection = { Trace.endSection() },
        logger = { tag, message -> Log.i(tag, message) }
    )

    private val appStartAtMs = nowMs()

    fun trace(stageName: String, detail: String? = null) {
        logger(LOG_TAG, formatMessage(stageName = stageName, detail = detail))
    }

    fun <T> measure(stageName: String, detail: String? = null, block: () -> T): T {
        beginSection(stageName)
        val startedAt = nowMs()
        return try {
            block()
        } finally {
            val durationMs = nowMs() - startedAt
            endSection()
            logger(
                LOG_TAG,
                formatMessage(stageName = stageName, detail = detail, durationMs = durationMs)
            )
        }
    }

    suspend fun <T> measureSuspend(
        stageName: String,
        detail: String? = null,
        block: suspend () -> T
    ): T {
        beginSection(stageName)
        val startedAt = nowMs()
        return try {
            block()
        } finally {
            val durationMs = nowMs() - startedAt
            endSection()
            logger(
                LOG_TAG,
                formatMessage(stageName = stageName, detail = detail, durationMs = durationMs)
            )
        }
    }

    private fun formatMessage(stageName: String, detail: String?, durationMs: Long? = null): String {
        val elapsedSinceStart = nowMs() - appStartAtMs
        return buildString {
            append("+")
            append(elapsedSinceStart)
            append("ms | ")
            append(stageName)
            if (!detail.isNullOrBlank()) {
                append(" | ")
                append(detail)
            }
            if (durationMs != null) {
                append(" | ")
                append(durationMs)
                append("ms")
            }
        }
    }

    private companion object {
        private const val LOG_TAG = "AppStartup"
    }
}
