package com.chen.memorizewords.domain.practice

import com.chen.memorizewords.domain.practice.model.ExamCategory
import com.chen.memorizewords.domain.practice.model.ExamPracticeAnswerSubmission
import com.chen.memorizewords.domain.practice.model.ExamQuestionType
import com.chen.memorizewords.domain.practice.model.WordExamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamPracticeSessionPolicyTest {

    private val policy = ExamPracticeSessionPolicy()

    @Test
    fun `single choice submission is graded and counted in report`() {
        val item = singleChoiceItem()
        val submission = ExamPracticeAnswerSubmission(
            itemId = item.id,
            answerIndexes = listOf(1),
            submitCount = 2
        )

        val report = policy.buildReport(
            items = listOf(item),
            submissionsByItemId = mapOf(item.id to submission)
        )
        val summary = policy.buildSummary(
            items = listOf(item),
            submissionsByItemId = mapOf(item.id to submission),
            report = report
        )

        assertEquals(1, report.answeredCount)
        assertEquals(1, report.correctCount)
        assertEquals(100, report.accuracyPercent)
        assertEquals(1, summary.questionCount)
        assertEquals(1, summary.completedCount)
        assertEquals(1, summary.correctCount)
        assertEquals(2, summary.submitCount)
    }

    @Test
    fun `viewed answer without submission is tracked as revealed`() {
        val item = matchingItem()
        val submission = ExamPracticeAnswerSubmission(
            itemId = item.id,
            viewedAnswer = true
        )

        val answerRecord = policy.toAnswerRecord(item, submission)

        assertEquals(PracticeAnswerStatus.REVEALED, answerRecord?.status)
        assertTrue(answerRecord?.expectedAnswer?.isNotBlank() == true)
    }

    private fun singleChoiceItem(): WordExamItem {
        return WordExamItem(
            id = 11L,
            wordId = 101L,
            questionType = ExamQuestionType.SINGLE_CHOICE,
            examCategory = ExamCategory.CET4,
            paperName = "paper",
            difficultyLevel = 1,
            sortOrder = 1,
            groupKey = null,
            contentText = "question",
            contextText = null,
            options = listOf("wrong", "right"),
            answerIndexes = listOf(1)
        )
    }

    private fun matchingItem(): WordExamItem {
        return WordExamItem(
            id = 12L,
            wordId = 101L,
            questionType = ExamQuestionType.MATCHING,
            examCategory = ExamCategory.CET4,
            paperName = "paper",
            difficultyLevel = 1,
            sortOrder = 2,
            groupKey = null,
            contentText = "match",
            contextText = null,
            leftItems = listOf("a"),
            rightItems = listOf("b"),
            answerIndexes = listOf(0)
        )
    }
}
