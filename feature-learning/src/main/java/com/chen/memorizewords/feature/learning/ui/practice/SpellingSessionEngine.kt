package com.chen.memorizewords.feature.learning.ui.practice

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.feature.learning.R
import java.util.Locale
import kotlin.math.max

internal data class SpellingHintResult(
    val answer: String,
    val hintLockedLength: Int
)

internal class SpellingSessionEngine(
    private val resourceProvider: ResourceProvider
) {

    fun reconcileKeyboardInput(
        answerWord: String,
        hintLockedLength: Int,
        input: String
    ): String {
        val sanitized = sanitizeAnswerInput(answerWord, input)
        return when {
            sanitized.length < hintLockedLength ->
                answerWord.take(hintLockedLength) + sanitized.drop(hintLockedLength)

            !sanitized.startsWith(answerWord.take(hintLockedLength)) ->
                answerWord.take(hintLockedLength) + sanitized.drop(hintLockedLength)

            else -> sanitized
        }
    }

    fun sanitizeAnswerInput(answerWord: String, raw: String): String {
        val trimmed = raw.trim().uppercase(Locale.ROOT)
        if (answerWord.isBlank()) return ""
        return trimmed.take(answerWord.length)
    }

    fun applyHint(
        answerWord: String,
        currentAnswer: String,
        hintLockedLength: Int
    ): SpellingHintResult? {
        if (answerWord.isBlank()) return null
        val revealIndex = currentAnswer.indices.firstOrNull { idx ->
            currentAnswer.getOrNull(idx)?.uppercaseChar() != answerWord[idx]
        } ?: currentAnswer.length
        if (revealIndex !in answerWord.indices) return null
        val builder = StringBuilder(answerWord.take(hintLockedLength))
        for (index in hintLockedLength until revealIndex) {
            builder.append(currentAnswer.getOrNull(index) ?: "")
        }
        builder.append(answerWord[revealIndex])
        if (revealIndex < currentAnswer.lastIndex) {
            builder.append(currentAnswer.substring(revealIndex + 1))
        }
        return SpellingHintResult(
            answer = sanitizeAnswerInput(answerWord, builder.toString()),
            hintLockedLength = max(hintLockedLength, revealIndex + 1)
        )
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
