package com.chen.memorizewords.domain.practice

import com.chen.memorizewords.domain.practice.model.ExamPracticeAnswerSubmission
import com.chen.memorizewords.domain.practice.model.ExamQuestionType
import com.chen.memorizewords.domain.practice.model.WordExamItem
import java.util.Locale

data class ExamPracticeGradingResult(
    val completed: Boolean = false,
    val correct: Boolean = false
)

data class ExamPracticeSessionSummary(
    val questionCount: Int = 0,
    val completedCount: Int = 0,
    val correctCount: Int = 0,
    val submitCount: Int = 0
)

class ExamPracticeSessionPolicy(
    private val reportPolicy: PracticeReportPolicy = PracticeReportPolicy()
) {
    fun grade(
        item: WordExamItem,
        submission: ExamPracticeAnswerSubmission?
    ): ExamPracticeGradingResult {
        if (submission == null || item.questionType !in OBJECTIVE_TYPES) {
            return ExamPracticeGradingResult()
        }
        return when (item.questionType) {
            ExamQuestionType.SINGLE_CHOICE -> {
                val selected = submission.answerIndexes.firstOrNull()
                    ?: return ExamPracticeGradingResult()
                ExamPracticeGradingResult(
                    completed = true,
                    correct = item.answerIndexes == listOf(selected)
                )
            }

            ExamQuestionType.CLOZE -> {
                if (submission.answers.isEmpty()) return ExamPracticeGradingResult()
                val expected = item.answers.map(::normalizeText)
                val actual = submission.answers.map(::normalizeText)
                ExamPracticeGradingResult(
                    completed = true,
                    correct = expected == actual
                )
            }

            ExamQuestionType.MATCHING -> {
                if (submission.answerIndexes.isEmpty()) return ExamPracticeGradingResult()
                ExamPracticeGradingResult(
                    completed = submission.answerIndexes.size == item.answerIndexes.size,
                    correct = submission.answerIndexes == item.answerIndexes
                )
            }

            ExamQuestionType.PASSAGE,
            ExamQuestionType.TRANSLATION -> ExamPracticeGradingResult()
        }
    }

    fun buildReport(
        items: List<WordExamItem>,
        submissionsByItemId: Map<Long, ExamPracticeAnswerSubmission>
    ): PracticeReport {
        val objectiveItems = items.filter { it.questionType in OBJECTIVE_TYPES }
        return reportPolicy.buildReport(
            totalQuestionCount = objectiveItems.size,
            history = objectiveItems.mapNotNull { item ->
                toAnswerRecord(item, submissionsByItemId[item.id])
            }
        )
    }

    fun buildSummary(
        items: List<WordExamItem>,
        submissionsByItemId: Map<Long, ExamPracticeAnswerSubmission>,
        report: PracticeReport = buildReport(items, submissionsByItemId)
    ): ExamPracticeSessionSummary {
        val objectiveItems = items.filter { it.questionType in OBJECTIVE_TYPES }
        if (objectiveItems.isEmpty()) return ExamPracticeSessionSummary()
        val submitCount = objectiveItems.sumOf { item ->
            val submission = submissionsByItemId[item.id]
            if (grade(item, submission).completed) {
                submission?.submitCount?.coerceAtLeast(1) ?: 0
            } else {
                0
            }
        }
        return ExamPracticeSessionSummary(
            questionCount = objectiveItems.size,
            completedCount = report.answeredCount,
            correctCount = report.correctCount,
            submitCount = submitCount
        )
    }

    fun toAnswerRecord(
        item: WordExamItem,
        submission: ExamPracticeAnswerSubmission?
    ): PracticeAnswerRecord? {
        val grading = grade(item, submission)
        return when {
            grading.completed -> PracticeAnswerRecord(
                questionId = questionId(item),
                wordId = item.wordId,
                status = if (grading.correct) {
                    PracticeAnswerStatus.CORRECT
                } else {
                    PracticeAnswerStatus.WRONG
                },
                submittedAnswer = submittedAnswer(item, submission),
                expectedAnswer = expectedAnswer(item)
            )

            submission?.viewedAnswer == true -> PracticeAnswerRecord(
                questionId = questionId(item),
                wordId = item.wordId,
                status = PracticeAnswerStatus.REVEALED,
                submittedAnswer = null,
                expectedAnswer = expectedAnswer(item)
            )

            else -> null
        }
    }

    private fun submittedAnswer(
        item: WordExamItem,
        submission: ExamPracticeAnswerSubmission?
    ): String? {
        submission ?: return null
        return when (item.questionType) {
            ExamQuestionType.SINGLE_CHOICE -> {
                submission.answerIndexes.firstOrNull()?.let(item.options::getOrNull)
            }

            ExamQuestionType.CLOZE -> submission.answers
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "|")

            ExamQuestionType.MATCHING -> submission.answerIndexes
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "|")

            ExamQuestionType.PASSAGE,
            ExamQuestionType.TRANSLATION -> null
        }
    }

    private fun expectedAnswer(item: WordExamItem): String {
        return when (item.questionType) {
            ExamQuestionType.SINGLE_CHOICE -> {
                item.answerIndexes.mapNotNull(item.options::getOrNull).joinToString(separator = "|")
            }

            ExamQuestionType.CLOZE -> item.answers.joinToString(separator = "|")
            ExamQuestionType.MATCHING -> item.answerIndexes.joinToString(separator = "|")
            ExamQuestionType.PASSAGE,
            ExamQuestionType.TRANSLATION -> ""
        }
    }

    private fun questionId(item: WordExamItem): String {
        return "exam:${item.id}:${item.questionType.name}"
    }

    private fun normalizeText(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    private companion object {
        val OBJECTIVE_TYPES = setOf(
            ExamQuestionType.SINGLE_CHOICE,
            ExamQuestionType.CLOZE,
            ExamQuestionType.MATCHING
        )
    }
}
