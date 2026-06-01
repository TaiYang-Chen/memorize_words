package com.chen.memorizewords.domain.practice

import com.chen.memorizewords.domain.practice.speech.ShadowingAnalysisSource
import com.chen.memorizewords.domain.practice.speech.ShadowingAudioIssue
import com.chen.memorizewords.domain.practice.speech.ShadowingAudioIssueSeverity
import com.chen.memorizewords.domain.practice.speech.ShadowingAudioIssueType
import com.chen.memorizewords.domain.practice.speech.ShadowingEvaluationResult
import com.chen.memorizewords.domain.practice.speech.SpeechProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowingPracticeSessionPolicyTest {

    private val policy = ShadowingPracticeSessionPolicy()

    @Test
    fun `silent or low score attempt stays in review`() {
        val lowScore = evaluation(totalScore = 72)
        val silentHighScore = evaluation(
            totalScore = 92,
            audioIssues = listOf(
                ShadowingAudioIssue(
                    type = ShadowingAudioIssueType.MOSTLY_SILENT,
                    severity = ShadowingAudioIssueSeverity.WARNING
                )
            )
        )

        assertTrue(policy.shouldScheduleReview(lowScore, errorMessage = ""))
        assertTrue(policy.shouldScheduleReview(silentHighScore, errorMessage = ""))
        assertTrue(policy.shouldScheduleReview(evaluation = null, errorMessage = "network"))
        assertFalse(policy.shouldScheduleReview(evaluation(totalScore = 88), errorMessage = ""))
    }

    @Test
    fun `summary counts best passing word and total submissions`() {
        val summary = policy.buildSummary(
            totalQuestionCount = 2,
            attemptsByWordId = mapOf(
                1L to listOf(
                    ShadowingPracticeAttemptOutcome(
                        wordId = 1L,
                        wordText = "apple",
                        attemptIndex = 0,
                        evaluation = evaluation(totalScore = 64)
                    ),
                    ShadowingPracticeAttemptOutcome(
                        wordId = 1L,
                        wordText = "apple",
                        attemptIndex = 1,
                        evaluation = evaluation(totalScore = 85)
                    )
                ),
                2L to listOf(
                    ShadowingPracticeAttemptOutcome(
                        wordId = 2L,
                        wordText = "banana",
                        attemptIndex = 0,
                        errorMessage = "scoring unavailable"
                    )
                )
            )
        )

        assertEquals(2, summary.questionCount)
        assertEquals(2, summary.completedCount)
        assertEquals(1, summary.correctCount)
        assertEquals(3, summary.submitCount)
    }

    @Test
    fun `answer record marks retry required audio as wrong`() {
        val passed = policy.toAnswerRecord(
            ShadowingPracticeAttemptOutcome(
                wordId = 3L,
                wordText = "cat",
                attemptIndex = 0,
                evaluation = evaluation(totalScore = 90)
            )
        )
        val retryRequired = policy.toAnswerRecord(
            ShadowingPracticeAttemptOutcome(
                wordId = 3L,
                wordText = "cat",
                attemptIndex = 1,
                evaluation = evaluation(
                    totalScore = 90,
                    audioIssues = listOf(
                        ShadowingAudioIssue(
                            type = ShadowingAudioIssueType.TOO_FAST,
                            severity = ShadowingAudioIssueSeverity.WARNING
                        )
                    )
                )
            )
        )

        assertEquals(PracticeAnswerStatus.CORRECT, passed?.status)
        assertEquals(PracticeAnswerStatus.WRONG, retryRequired?.status)
        assertEquals("shadowing:3:1", retryRequired?.questionId)
    }

    private fun evaluation(
        totalScore: Int,
        audioIssues: List<ShadowingAudioIssue> = emptyList()
    ): ShadowingEvaluationResult {
        return ShadowingEvaluationResult(
            provider = SpeechProviderType.BAIDU,
            traceId = "trace",
            totalScore = totalScore,
            pronunciationScore = totalScore,
            fluencyScore = totalScore,
            recognizedText = "spoken",
            audioIssues = audioIssues,
            analysisSource = ShadowingAnalysisSource.PROVIDER_ONLY
        )
    }
}
