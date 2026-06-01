package com.chen.memorizewords.domain.practice.policy
import java.util.Locale
import kotlin.math.max

data class SpellingHint(
    val answer: String,
    val hintLockedLength: Int
)

class SpellingAnswerPolicy {
    fun sanitizeAnswerInput(answerWord: String, raw: String): String {
        val trimmed = raw.trim().uppercase(Locale.ROOT)
        if (answerWord.isBlank()) return ""
        return trimmed.take(answerWord.length)
    }

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

    fun applyHint(
        answerWord: String,
        currentAnswer: String,
        hintLockedLength: Int
    ): SpellingHint? {
        if (answerWord.isBlank()) return null
        val revealIndex = currentAnswer.indices.firstOrNull { index ->
            currentAnswer.getOrNull(index)?.uppercaseChar() != answerWord[index]
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
        return SpellingHint(
            answer = sanitizeAnswerInput(answerWord, builder.toString()),
            hintLockedLength = max(hintLockedLength, revealIndex + 1)
        )
    }

    fun isCorrect(input: String, answerWord: String): Boolean {
        return input.trim().equals(answerWord.trim(), ignoreCase = true)
    }

    fun isCorrectIgnoringWhitespace(input: String, answerWord: String): Boolean {
        return input.normalizeWithoutWhitespace() == answerWord.normalizeWithoutWhitespace()
    }

    private fun String.normalizeWithoutWhitespace(): String {
        return trim().lowercase(Locale.ROOT).filterNot(Char::isWhitespace)
    }
}
