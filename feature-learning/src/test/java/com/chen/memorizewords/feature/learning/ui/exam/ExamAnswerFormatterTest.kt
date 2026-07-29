package com.chen.memorizewords.feature.learning.ui.exam

import com.chen.memorizewords.domain.practice.model.ExamCategory
import com.chen.memorizewords.domain.practice.model.ExamQuestionType
import com.chen.memorizewords.domain.practice.model.WordExamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExamAnswerFormatterTest {

    @Test
    fun `single choice includes letter and option text`() {
        val item = examItem(
            type = ExamQuestionType.SINGLE_CHOICE,
            options = listOf("presence", "gift", "pressure", "progress"),
            answerIndexes = listOf(0)
        )

        assertEquals(
            ExamAnswerContent(heading = "\u7b54\u6848", body = "A  presence"),
            answerContentFor(item)
        )
    }

    @Test
    fun `cloze answers keep their order`() {
        val item = examItem(
            type = ExamQuestionType.CLOZE,
            answers = listOf("first", "second")
        )

        assertEquals("1. first\n2. second", answerContentFor(item)?.body)
    }

    @Test
    fun `matching answers use compact numbered mapping`() {
        val item = examItem(
            type = ExamQuestionType.MATCHING,
            answerIndexes = listOf(0, 2, 1)
        )

        assertEquals("1-A   2-C   3-B", answerContentFor(item)?.body)
    }

    @Test
    fun `translation uses reference translation heading`() {
        val item = examItem(
            type = ExamQuestionType.TRANSLATION,
            answers = listOf("The team regained confidence.")
        )

        assertEquals(
            ExamAnswerContent(
                heading = "\u53c2\u8003\u8bd1\u6587",
                body = "The team regained confidence."
            ),
            answerContentFor(item)
        )
    }

    @Test
    fun `passage does not create an answer block`() {
        assertNull(answerContentFor(examItem(type = ExamQuestionType.PASSAGE)))
    }

    private fun examItem(
        type: ExamQuestionType,
        options: List<String> = emptyList(),
        answers: List<String> = emptyList(),
        answerIndexes: List<Int> = emptyList()
    ): WordExamItem {
        return WordExamItem(
            id = 1L,
            wordId = 10L,
            questionType = type,
            examCategory = ExamCategory.CET4,
            paperName = "2023 CET-4",
            difficultyLevel = 3,
            sortOrder = 1,
            groupKey = null,
            contentText = "Question",
            contextText = null,
            options = options,
            answers = answers,
            answerIndexes = answerIndexes
        )
    }
}
