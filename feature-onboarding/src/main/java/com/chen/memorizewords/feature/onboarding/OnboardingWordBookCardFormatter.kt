package com.chen.memorizewords.feature.onboarding

import com.chen.memorizewords.domain.model.wordbook.WordBook
import java.util.Locale
import kotlin.math.absoluteValue

internal fun formatPlaceholderLearnerCount(wordBook: WordBook): String {
    val seed = (wordBook.id * 48_271L).absoluteValue
    val learners = 12_000 + (seed % 43_000).toInt()
    return "${formatCountInTenThousands(learners)} \u4eba\u5728\u5b66"
}

private fun formatCountInTenThousands(value: Int): String {
    if (value < 10_000) return value.toString()
    val scaled = value / 10_000.0
    val text = String.format(Locale.US, "%.1f", scaled)
    return text.removeSuffix(".0") + "w"
}
