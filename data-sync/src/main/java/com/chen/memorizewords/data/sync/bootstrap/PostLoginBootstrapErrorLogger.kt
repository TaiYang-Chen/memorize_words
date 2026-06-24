package com.chen.memorizewords.data.sync.bootstrap

import android.content.Context
import com.chen.memorizewords.core.network.remote.HttpStatusException
import com.chen.memorizewords.core.network.remote.UnauthorizedNetworkException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostLoginBootstrapErrorLogger @Inject constructor(
    @ApplicationContext context: Context
) {
    private val logsDir = File(context.filesDir, "logs")
    private val logFile = File(logsDir, LOG_FILE_NAME)
    private val rotatedLogFile = File(logsDir, "$LOG_FILE_NAME.1")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)

    @Synchronized
    fun logFailure(
        source: String,
        throwable: Throwable,
        stepName: String? = null,
        classification: String? = null
    ) {
        runCatching {
            logsDir.mkdirs()
            rotateIfNeeded()
            logFile.appendText(
                buildLogEntry(
                    source = source,
                    throwable = throwable,
                    stepName = stepName ?: throwable.bootstrapStepName(),
                    classification = classification
                )
            )
        }
    }

    private fun rotateIfNeeded() {
        if (!logFile.exists() || logFile.length() <= MAX_LOG_BYTES) return
        if (rotatedLogFile.exists()) {
            rotatedLogFile.delete()
        }
        logFile.renameTo(rotatedLogFile)
    }

    private fun buildLogEntry(
        source: String,
        throwable: Throwable,
        stepName: String?,
        classification: String?
    ): String {
        return buildString {
            appendLine("----- post-login bootstrap failure -----")
            appendLine("time=${dateFormat.format(Date())}")
            appendLine("source=$source")
            if (!stepName.isNullOrBlank()) {
                appendLine("step=$stepName")
            }
            if (!classification.isNullOrBlank()) {
                appendLine("classification=$classification")
            }
            appendHttpDetails(throwable.rootDiagnosticCause())
            appendLine("exception=${throwable.javaClass.name}")
            appendLine("message=${throwable.message.orEmpty()}")
            appendLine("causeChain=${throwable.causeChainSummary()}")
            appendLine()
        }
    }

    private fun StringBuilder.appendHttpDetails(throwable: Throwable) {
        when (throwable) {
            is HttpStatusException -> {
                appendLine("httpStatus=${throwable.code}")
                appendLine("httpMessage=${throwable.message.orEmpty()}")
            }

            is UnauthorizedNetworkException -> {
                appendLine("unauthorizedMessage=${throwable.message.orEmpty()}")
            }
        }
    }

    private companion object {
        const val LOG_FILE_NAME = "post_login_bootstrap.log"
        const val MAX_LOG_BYTES = 256 * 1024L
    }
}

internal fun Throwable.bootstrapStepName(): String? {
    return when (this) {
        is ServerBootstrapStepException -> stepName
        else -> cause?.bootstrapStepName()
    }
}

internal fun Throwable.rootDiagnosticCause(): Throwable {
    return when (this) {
        is ServerBootstrapStepException -> cause?.rootDiagnosticCause() ?: this
        else -> this
    }
}

private fun Throwable.causeChainSummary(): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = this
    while (current != null) {
        parts += "${current.javaClass.simpleName}: ${current.message.orEmpty()}"
        current = current.cause
    }
    return parts.joinToString(" <- ")
}
