package com.chen.memorizewords.speech

import com.chen.memorizewords.speech.api.ShadowingAnalysisSource
import com.chen.memorizewords.speech.api.ShadowingAudioIssue
import com.chen.memorizewords.speech.api.ShadowingAudioIssueSeverity
import com.chen.memorizewords.speech.api.ShadowingAudioIssueType
import com.chen.memorizewords.speech.api.ShadowingRecordingMetadata
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Singleton
class BaiduShadowingScorer @Inject constructor() {

    internal fun score(
        referenceText: String,
        recognizedText: String,
        recordingMetadata: ShadowingRecordingMetadata
    ): ShadowingScores {
        val normalizedReference = normalize(referenceText)
        val normalizedRecognized = normalize(recognizedText)
        val audioAnalysis = analyzeRecording(
            referenceText = normalizedReference,
            recordingMetadata = recordingMetadata
        )
        if (normalizedReference.isBlank() || normalizedRecognized.isBlank()) {
            return ShadowingScores(
                totalScore = 0,
                pronunciationScore = 0,
                fluencyScore = 0,
                intonationScore = audioAnalysis.intonationScore,
                stressScore = audioAnalysis.stressScore,
                speedScore = audioAnalysis.speedScore,
                audioIssues = audioAnalysis.audioIssues,
                analysisSource = audioAnalysis.analysisSource,
                detailSourceNote = audioAnalysis.detailSourceNote
            )
        }

        val pronunciationRatio = similarity(normalizedReference, normalizedRecognized)
        val tokenCoverage = tokenCoverage(normalizedReference, normalizedRecognized)
        val lengthRatio = lengthRatio(normalizedReference, normalizedRecognized)
        val pronunciationScore = (pronunciationRatio * 100).roundToInt().coerceIn(0, 100)
        val fluencyScore = (
            (
                (tokenCoverage * 0.55) +
                    (lengthRatio * 0.25) +
                    (audioAnalysis.activeSpeechRatio * 0.20)
                ) * 100
            )
            .roundToInt()
            .coerceIn(0, 100)
        val totalScore = (
            (pronunciationScore * 0.6) +
                (fluencyScore * 0.2) +
                (audioAnalysis.intonationScore * 0.08) +
                (audioAnalysis.stressScore * 0.06) +
                (audioAnalysis.speedScore * 0.06)
            )
            .roundToInt()
            .coerceIn(0, 100)

        return ShadowingScores(
            totalScore = totalScore,
            pronunciationScore = pronunciationScore,
            fluencyScore = fluencyScore,
            intonationScore = audioAnalysis.intonationScore,
            stressScore = audioAnalysis.stressScore,
            speedScore = audioAnalysis.speedScore,
            audioIssues = audioAnalysis.audioIssues,
            analysisSource = audioAnalysis.analysisSource,
            detailSourceNote = audioAnalysis.detailSourceNote
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

    private fun analyzeRecording(
        referenceText: String,
        recordingMetadata: ShadowingRecordingMetadata
    ): AudioAnalysis {
        val samples = recordingMetadata.waveformSamples
            .map { it.coerceIn(0, 100) }
            .ifEmpty { listOf(18, 22, 20, 28, 24, 22, 18, 16) }
        val durationMs = recordingMetadata.durationMs.coerceAtLeast(0L)
        val activeSpeechRatio = samples.count { it >= ACTIVE_SAMPLE_THRESHOLD }
            .toDouble() / samples.size.toDouble()
        val averageLevel = samples.average()
        val maxLevel = samples.maxOrNull() ?: 0
        val minLevel = samples.minOrNull() ?: 0
        val dynamicRange = (maxLevel - minLevel).coerceAtLeast(0)
        val contourVariation = if (samples.size <= 1) {
            0.0
        } else {
            samples.zipWithNext { left, right -> (right - left).absoluteValue }
                .average() / 100.0
        }
        val dominantPeakRatio = maxLevel.toDouble() / averageLevel.coerceAtLeast(1.0)
        val expectedDurationMs = estimateExpectedDurationMs(referenceText)
        val durationRatio = if (durationMs <= 0L) {
            1.0
        } else {
            durationMs.toDouble() / expectedDurationMs.toDouble()
        }

        val intonationScore = (
            (
                contourFitScore(contourVariation, target = 0.20, tolerance = 0.18) * 0.45 +
                    activeSpeechRatio.coerceIn(0.0, 1.0) * 0.25 +
                    (dynamicRange / 34.0).coerceIn(0.0, 1.0) * 0.30
                ) * 100
            )
            .roundToInt()
            .coerceIn(45, 98)
        val stressScore = (
            (
                contourFitScore(dominantPeakRatio, target = 2.2, tolerance = 1.6) * 0.55 +
                    (dynamicRange / 32.0).coerceIn(0.0, 1.0) * 0.45
                ) * 100
            )
            .roundToInt()
            .coerceIn(40, 98)
        val speedScore = (
            contourFitScore(durationRatio, target = 1.0, tolerance = 0.65) * 100
            )
            .roundToInt()
            .coerceIn(35, 100)

        val audioIssues = buildList {
            if (durationMs in 1 until 500L || durationRatio < 0.55) {
                add(
                    ShadowingAudioIssue(
                        type = ShadowingAudioIssueType.TOO_FAST,
                        severity = ShadowingAudioIssueSeverity.WARNING
                    )
                )
            }
            if (durationRatio > 1.85) {
                add(
                    ShadowingAudioIssue(
                        type = ShadowingAudioIssueType.TOO_SLOW,
                        severity = ShadowingAudioIssueSeverity.INFO
                    )
                )
            }
            if (activeSpeechRatio < 0.28) {
                add(
                    ShadowingAudioIssue(
                        type = ShadowingAudioIssueType.MOSTLY_SILENT,
                        severity = ShadowingAudioIssueSeverity.WARNING
                    )
                )
            }
            if (averageLevel < 18.0) {
                add(
                    ShadowingAudioIssue(
                        type = ShadowingAudioIssueType.LOW_VOLUME,
                        severity = ShadowingAudioIssueSeverity.INFO
                    )
                )
            }
            if (averageLevel >= 28.0 && dynamicRange < 12) {
                add(
                    ShadowingAudioIssue(
                        type = ShadowingAudioIssueType.ENVIRONMENT_NOISE,
                        severity = ShadowingAudioIssueSeverity.INFO
                    )
                )
            }
        }

        return AudioAnalysis(
            intonationScore = intonationScore,
            stressScore = stressScore,
            speedScore = speedScore,
            activeSpeechRatio = activeSpeechRatio,
            audioIssues = audioIssues,
            analysisSource = ShadowingAnalysisSource.PROVIDER_PLUS_LOCAL,
            detailSourceNote = LOCAL_ANALYSIS_NOTE
        )
    }

    private fun estimateExpectedDurationMs(referenceText: String): Long {
        val syllableCount = estimateSyllableCount(referenceText)
        return (450L + syllableCount * 180L + referenceText.length * 22L).coerceAtLeast(650L)
    }

    private fun estimateSyllableCount(referenceText: String): Int {
        val compact = referenceText.replace(" ", "")
        if (compact.isBlank()) return 1
        val groups = "[aeiouy]+".toRegex().findAll(compact).count()
        return groups.coerceAtLeast(1)
    }

    private fun contourFitScore(value: Double, target: Double, tolerance: Double): Double {
        if (tolerance <= 0.0) return 1.0
        return (1.0 - ((value - target).absoluteValue / tolerance)).coerceIn(0.0, 1.0)
    }

    private data class AudioAnalysis(
        val intonationScore: Int,
        val stressScore: Int,
        val speedScore: Int,
        val activeSpeechRatio: Double,
        val audioIssues: List<ShadowingAudioIssue>,
        val analysisSource: ShadowingAnalysisSource,
        val detailSourceNote: String
    )

    companion object {
        private const val ACTIVE_SAMPLE_THRESHOLD = 22
        private const val LOCAL_ANALYSIS_NOTE =
            "Detailed intonation, stress, speed, and silence/noise checks are local placeholders for now."
    }
}

internal data class ShadowingScores(
    val totalScore: Int,
    val pronunciationScore: Int,
    val fluencyScore: Int,
    val intonationScore: Int,
    val stressScore: Int,
    val speedScore: Int,
    val audioIssues: List<ShadowingAudioIssue>,
    val analysisSource: ShadowingAnalysisSource,
    val detailSourceNote: String
)
