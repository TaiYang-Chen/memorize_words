package com.chen.memorizewords.feature.learning.ui.practice

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.practice.policy.SpellingAnswerPolicy
import com.chen.memorizewords.feature.learning.R
import kotlin.math.max

internal data class SpellingHintResult(
    val answer: String,
    val hintLockedLength: Int
)

internal class SpellingUiHelper(
    private val resourceProvider: ResourceProvider,
    private val spellingAnswerPolicy: SpellingAnswerPolicy = SpellingAnswerPolicy()
) {
    fun reconcileKeyboardInput(
        answerWord: String,
        hintLockedLength: Int,
        input: String
    ): String = spellingAnswerPolicy.reconcileKeyboardInput(answerWord, hintLockedLength, input)

    fun applyHint(
        answerWord: String,
        currentAnswer: String,
        hintLockedLength: Int
    ): SpellingHintResult? {
        return spellingAnswerPolicy.applyHint(answerWord, currentAnswer, hintLockedLength)
            ?.let { hint ->
                SpellingHintResult(
                    answer = hint.answer,
                    hintLockedLength = hint.hintLockedLength
                )
            }
    }

    fun buildLetterPoolChars(word: String): List<Char> {
        if (word.isBlank()) return emptyList()
        val distractorSource = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            .filterNot { word.contains(it) }
            .toMutableList()
        val pool = word.toMutableList()
        val targetSize = max(word.length, 11)
        while (pool.size < targetSize) {
            if (distractorSource.isEmpty()) {
                pool.add(('A' + (pool.size % 26)))
            } else {
                pool.add(distractorSource.removeAt(0))
            }
        }
        return pool.shuffled()
    }

    fun buildLetterItems(
        poolChars: List<Char>,
        answer: String
    ): List<SpellingPracticeViewModel.LetterItem> {
        if (poolChars.isEmpty()) return emptyList()
        val answerCounts = answer.groupingBy { it }.eachCount()
        return poolChars.mapIndexed { idx, c ->
            val consumed = answerCounts[c] ?: 0
            val poolCountBefore = poolChars.take(idx + 1).count { it == c }
            SpellingPracticeViewModel.LetterItem(
                id = idx,
                letter = c,
                enabled = poolCountBefore > consumed
            )
        }
    }

    fun buildAnswerSlots(
        answerWord: String,
        currentAnswer: String,
        hintLockedLength: Int
    ): List<SpellingPracticeViewModel.AnswerSlot> {
        if (answerWord.isBlank()) return emptyList()
        return answerWord.indices.map { index ->
            SpellingPracticeViewModel.AnswerSlot(
                letter = currentAnswer.getOrNull(index)?.toString().orEmpty(),
                isHintLocked = index < hintLockedLength
            )
        }
    }

    fun buildRetryFeedback(answerWord: String, input: String): String {
        val mismatchCount = answerWord.indices.count { index ->
            input.getOrNull(index)?.uppercaseChar() != answerWord[index]
        } + (answerWord.length - input.length).coerceAtLeast(0)
        val count = mismatchCount.coerceAtLeast(1)
        return resourceProvider.getString(R.string.practice_spelling_retry_feedback, count)
    }

    fun buildSummaryText(summary: PracticeSessionSummary): String {
        val accuracy = if (summary.completedCount > 0) {
            (summary.correctCount * 100) / summary.completedCount
        } else {
            0
        }
        return resourceProvider.getString(
            R.string.practice_spelling_summary,
            summary.completedCount,
            summary.questionCount,
            summary.correctCount,
            accuracy,
            summary.submitCount
        )
    }
}
