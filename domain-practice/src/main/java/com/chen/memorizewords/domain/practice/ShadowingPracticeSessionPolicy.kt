package com.chen.memorizewords.domain.practice

import com.chen.memorizewords.domain.practice.speech.ShadowingAudioIssueType
import com.chen.memorizewords.domain.practice.speech.ShadowingEvaluationResult

data class ShadowingPracticeAttemptOutcome(
    val wordId: Long,
    val wordText: String,
    val attemptIndex: Int,
    val evaluation: ShadowingEvaluationResult? = null,
    val errorMessage: String = ""
)

data class ShadowingPracticeSessionSummary(
    val questionCount: Int = 0,
    val completedCount: Int = 0,
    val correctCount: Int = 0,
    val submitCount: Int = 0
)

class ShadowingPracticeSessionPolicy(
    private val passScore: Int = DEFAULT_PASS_SCORE
) {
    fun shouldScheduleReview(
        evaluation: ShadowingEvaluationResult?,
        errorMessage: String
    ): Boolean {
        if (evaluation == null) return errorMessage.isNotBlank()
        return evaluation.totalScore < passScore ||
            evaluation.audioIssues.any { issue ->
                issue.type == ShadowingAudioIssueType.MOSTLY_SILENT ||
                    issue.type == ShadowingAudioIssueType.TOO_FAST
            }
    }

    fun toAnswerRecord(outcome: ShadowingPracticeAttemptOutcome): PracticeAnswerRecord? {
        val evaluation = outcome.evaluation
        if (evaluation == null && outcome.errorMessage.isBlank()) return null
        return PracticeAnswerRecord(
            questionId = "shadowing:${outcome.wordId}:${outcome.attemptIndex}",
            wordId = outcome.wordId,
            status = if (
                evaluation != null &&
                !shouldScheduleReview(evaluation = evaluation, errorMessage = outcome.errorMessage)
            ) {
                PracticeAnswerStatus.CORRECT
            } else {
                PracticeAnswerStatus.WRONG
            },
            submittedAnswer = evaluation?.recognizedText?.takeIf { it.isNotBlank() }
                ?: outcome.errorMessage.takeIf { it.isNotBlank() },
            expectedAnswer = outcome.wordText,
            score = evaluation?.totalScore?.toFloat()
        )
    }

    fun buildSummary(
        totalQuestionCount: Int,
        attemptsByWordId: Map<Long, List<ShadowingPracticeAttemptOutcome>>
    ): ShadowingPracticeSessionSummary {
        if (totalQuestionCount <= 0) return ShadowingPracticeSessionSummary()
        val submittedOutcomesByWord = attemptsByWordId.mapValues { (_, outcomes) ->
            outcomes.filter { outcome ->
                outcome.evaluation != null || outcome.errorMessage.isNotBlank()
            }
        }
        return ShadowingPracticeSessionSummary(
            questionCount = totalQuestionCount,
            completedCount = submittedOutcomesByWord.values.count { outcomes -> outcomes.isNotEmpty() },
            correctCount = submittedOutcomesByWord.values.count { outcomes ->
                outcomes.any { outcome ->
                    val evaluation = outcome.evaluation
                    evaluation != null && evaluation.totalScore >= passScore
                }
            },
            submitCount = submittedOutcomesByWord.values.sumOf { outcomes -> outcomes.size }
        )
    }

    private companion object {
        const val DEFAULT_PASS_SCORE = 80
    }
}
