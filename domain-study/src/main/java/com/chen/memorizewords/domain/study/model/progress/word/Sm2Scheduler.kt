package com.chen.memorizewords.domain.study.model.progress.word

import kotlin.math.max
import kotlin.math.round

data class Sm2Result(
    val interval: Long,
    val ef: Double,
    val repetition: Int,
    val mastery: Int
)

fun calculateSm2Review(
    prevInterval: Long,
    prevEF: Double,
    prevRepetition: Int,
    quality: Int
): Sm2Result {
    val boundedQuality = quality.coerceIn(0, 5)
    var ef = prevEF
    ef += 0.1 - (5 - boundedQuality) * (0.08 + (5 - boundedQuality) * 0.02)
    if (ef < 1.3) ef = 1.3

    var repetition = prevRepetition
    val interval: Long = if (boundedQuality < 3) {
        repetition = 0
        1L
    } else {
        repetition++
        when (repetition) {
            1 -> 1L
            2 -> 6L
            else -> {
                val raw = round(prevInterval * ef)
                max(1L, raw.toLong())
            }
        }
    }

    val mastery = when {
        repetition >= 6 && ef >= 2.6 -> 5
        repetition >= 5 -> 4
        repetition >= 3 -> 3
        repetition >= 2 -> 2
        repetition >= 1 -> 1
        else -> 0
    }

    return Sm2Result(
        interval = interval,
        ef = ef,
        repetition = repetition,
        mastery = mastery
    )
}
