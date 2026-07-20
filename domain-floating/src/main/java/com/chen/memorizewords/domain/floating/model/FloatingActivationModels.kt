package com.chen.memorizewords.domain.floating.model

enum class FloatingActivationSource {
    HOME,
    CHARACTER_SELECTION
}

data class PendingFloatingActivation(
    val requestId: String,
    val targetPackId: String?,
    val source: FloatingActivationSource,
    val createdAtMs: Long,
    val committedAtMs: Long? = null
)

enum class FloatingActivationPhase {
    IDLE,
    NEEDS_DOWNLOAD,
    QUEUED,
    DOWNLOADING,
    INSTALLING,
    READY,
    FAILED
}

data class FloatingActivationSnapshot(
    val pending: PendingFloatingActivation? = null,
    val target: CharacterPackCatalogItem? = null,
    val download: CharacterPackDownloadState? = null,
    val phase: FloatingActivationPhase = FloatingActivationPhase.IDLE
)

enum class FloatingActivationPreparation {
    READY_FOR_PERMISSION,
    NEEDS_DOWNLOAD,
    NO_CHARACTER_AVAILABLE,
    SELECTION_REQUIRED,
    INELIGIBLE
}

enum class FloatingActivationContinuation {
    ACTIVATED,
    REQUIRES_PERMISSION,
    WAITING_FOR_CHARACTER,
    NO_PENDING_REQUEST,
    STALE_REQUEST,
    INELIGIBLE
}

enum class FloatingActivationEligibility {
    ELIGIBLE,
    AUTHENTICATION_REQUIRED,
    MEMBERSHIP_REQUIRED
}
