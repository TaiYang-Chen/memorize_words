package com.chen.memorizewords.domain.practice
enum class PracticeKind {
    LISTENING_MEANING,
    LISTENING_SPELLING,
    SHADOWING,
    AUDIO_LOOP,
    EXAM
}

enum class PracticeAnswerStatus {
    CORRECT,
    WRONG,
    REVEALED,
    SKIPPED
}

data class PracticeAnswerRecord(
    val questionId: String,
    val wordId: Long,
    val status: PracticeAnswerStatus,
    val submittedAnswer: String? = null,
    val expectedAnswer: String,
    val score: Float? = null
)

data class PracticeReport(
    val totalQuestionCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val skippedCount: Int,
    val revealedCount: Int,
    val accuracyPercent: Int
)
