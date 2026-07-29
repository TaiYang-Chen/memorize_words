package com.chen.memorizewords.feature.learning.ui.exam

import com.chen.memorizewords.domain.practice.model.ExamQuestionType
import com.chen.memorizewords.domain.practice.model.WordExamItem

internal data class ExamAnswerContent(
    val heading: String,
    val body: String
)

internal fun answerContentFor(item: WordExamItem): ExamAnswerContent? {
    return when (item.questionType) {
        ExamQuestionType.SINGLE_CHOICE -> ExamAnswerContent(
            heading = "\u7b54\u6848",
            body = formatChoiceAnswer(item)
        )

        ExamQuestionType.CLOZE -> ExamAnswerContent(
            heading = "\u7b54\u6848",
            body = formatOrderedAnswers(item.answers)
        )

        ExamQuestionType.MATCHING -> ExamAnswerContent(
            heading = "\u7b54\u6848",
            body = item.answerIndexes.mapIndexedNotNull { leftIndex, rightIndex ->
                optionPrefix(rightIndex)?.let { prefix -> "${leftIndex + 1}-$prefix" }
            }.joinToString("   ").ifBlank { NO_ANSWER }
        )

        ExamQuestionType.TRANSLATION -> ExamAnswerContent(
            heading = "\u53c2\u8003\u8bd1\u6587",
            body = item.answers.filter { it.isNotBlank() }.joinToString("\n\n").ifBlank { NO_ANSWER }
        )

        ExamQuestionType.PASSAGE -> null
    }
}

private fun formatChoiceAnswer(item: WordExamItem): String {
    val indexedAnswers = item.answerIndexes.distinct().mapNotNull { index ->
        val prefix = optionPrefix(index) ?: return@mapNotNull null
        val option = item.options.getOrNull(index).orEmpty()
        if (option.isBlank()) prefix else "$prefix  $option"
    }
    return indexedAnswers.ifEmpty { item.answers.filter { it.isNotBlank() } }
        .joinToString("\n")
        .ifBlank { NO_ANSWER }
}

private fun formatOrderedAnswers(answers: List<String>): String {
    val nonBlankAnswers = answers.filter { it.isNotBlank() }
    if (nonBlankAnswers.size <= 1) return nonBlankAnswers.firstOrNull() ?: NO_ANSWER
    return nonBlankAnswers.mapIndexed { index, answer -> "${index + 1}. $answer" }.joinToString("\n")
}

internal fun optionPrefix(index: Int): String? {
    return if (index in 0..25) ('A'.code + index).toChar().toString() else null
}

private const val NO_ANSWER = "\u6682\u65e0\u7b54\u6848"
