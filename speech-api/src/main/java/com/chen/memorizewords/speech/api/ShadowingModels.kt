package com.chen.memorizewords.speech.api

data class ShadowingRecordingMetadata(
    val durationMs: Long = 0L,
    val waveformSamples: List<Int> = emptyList()
)

enum class ShadowingAnalysisSource {
    PROVIDER_ONLY,
    LOCAL_PLACEHOLDER,
    PROVIDER_PLUS_LOCAL
}

enum class ShadowingAudioIssueSeverity {
    INFO,
    WARNING
}

enum class ShadowingAudioIssueType {
    LOW_VOLUME,
    MOSTLY_SILENT,
    TOO_FAST,
    TOO_SLOW,
    ENVIRONMENT_NOISE
}

data class ShadowingAudioIssue(
    val type: ShadowingAudioIssueType,
    val severity: ShadowingAudioIssueSeverity,
    val message: String? = null
)

data class ShadowingDetail(
    val text: String,
    val score: Int? = null,
    val expected: String? = null,
    val actual: String? = null,
    val issueType: String? = null,
    val message: String? = null
)

data class ShadowingRecordingQuality(
    val volumeScore: Int? = null,
    val speechRatio: Int? = null,
    val durationMs: Long? = null,
    val level: String? = null,
    val message: String? = null
)
