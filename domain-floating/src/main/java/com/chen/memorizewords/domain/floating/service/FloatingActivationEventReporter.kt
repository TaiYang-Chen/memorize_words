package com.chen.memorizewords.domain.floating.service

enum class FloatingActivationEvent {
    ACTIVATION_REQUESTED,
    SETUP_SHOWN,
    RESOLVED_CHARACTER_DOWNLOAD_SELECTED,
    OTHER_CHARACTER_SELECTED,
    DOWNLOAD_ENQUEUED,
    DOWNLOAD_STARTED,
    DOWNLOAD_SUCCEEDED,
    DOWNLOAD_FAILED,
    PERMISSION_REQUIRED,
    PERMISSION_GRANTED,
    PERMISSION_DENIED,
    ACTIVATION_COMMITTED,
    FLOATING_STARTED,
    ACTIVATION_CANCELLED,
    MISSING_PACK_DISABLED
}

interface FloatingActivationEventReporter {
    fun report(
        event: FloatingActivationEvent,
        attributes: Map<String, String> = emptyMap()
    )
}

fun interface FloatingActivationEligibilityChecker {
    suspend fun checkEligibility(): com.chen.memorizewords.domain.floating.model.FloatingActivationEligibility
}
