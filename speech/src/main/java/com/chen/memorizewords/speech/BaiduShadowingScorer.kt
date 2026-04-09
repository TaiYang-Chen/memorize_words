package com.chen.memorizewords.speech

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class BaiduShadowingScorer @Inject constructor() {

    internal fun score(referenceText: String, recognizedText: String): ShadowingScores {
        val normalizedReference = normalize(referenceText)
        val normalizedRecognized = normalize(recognizedText)
        if (normalizedReference.isBlank() || normalizedRecognized.isBlank()) {
            return ShadowingScores(
                totalScore = 0,
                pronunciationScore = 0,
                fluencyScore = 0
            )
        }
        val pronunciationRatio = similarity(normalizedReference, normalizedRecognized)
        val tokenCoverage = tokenCoverage(normalizedReference, normalizedRecognized)
        val lengthRatio = lengthRatio(normalizedReference, normalizedRecognized)
        val pronunciationScore = (pronunciationRatio * 100).roundToInt().coerceIn(0, 100)
        val fluencyScore = (((tokenCoverage * 0.7) + (lengthRatio * 0.3)) * 100)
            .roundToInt()
            .coerceIn(0, 100)
        val totalScore = ((pronunciationScore * 0.7) + (fluencyScore * 0.3))
            .roundToInt()
            .coerceIn(0, 100)
        return ShadowingScores(
            totalScore = totalScore,
            pronunciationScore = pronunciationScore,
            fluencyScore = fluencyScore
        )
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun similarity(reference: String, recognized: String): Double {
        val maxLength = maxOf(reference.length, recognized.length)
        if (maxLength == 0) return 1.0
        val distance = levenshtein(reference, recognized)
        return (1.0 - distance.toDouble() / maxLength.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun tokenCoverage(reference: String, recognized: String): Double {
        val referenceTokens = reference.split(" ").filter { it.isNotBlank() }
        val recognizedTokens = recognized.split(" ").filter { it.isNotBlank() }
        if (referenceTokens.isEmpty() || recognizedTokens.isEmpty()) return 0.0
        val matched = referenceTokens.count { token -> recognizedTokens.contains(token) }
        return matched.toDouble() / referenceTokens.size.toDouble()
    }

    private fun lengthRatio(reference: String, recognized: String): Double {
        val maxLength = maxOf(reference.length, recognized.length)
        if (maxLength == 0) return 1.0
        return (minOf(reference.length, recognized.length).toDouble() / maxLength.toDouble())
            .coerceIn(0.0, 1.0)
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        left.forEachIndexed { i, leftChar ->
            current[0] = i + 1
            right.forEachIndexed { j, rightChar ->
                val substitutionCost = if (leftChar == rightChar) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + substitutionCost
                )
            }
            current.copyInto(previous)
        }
        return previous[right.length]
    }
}

internal data class ShadowingScores(
    val totalScore: Int,
    val pronunciationScore: Int,
    val fluencyScore: Int
)
