package com.chen.memorizewords.feature.wordbook.shop

import android.content.Context
import com.chen.memorizewords.domain.wordbook.model.WordBook
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue

internal fun formatBookShopWordCount(totalWords: Int): String {
    return "${NumberFormat.getIntegerInstance().format(totalWords)} 词"
}

internal fun formatBookShopMeta(context: Context, wordBook: WordBook): String {
    return formatBookShopLearnerCount(wordBook)
}

internal fun formatBookShopLearnerCount(wordBook: WordBook): String {
    val seed = (wordBook.id * 48_271L).absoluteValue
    val learners = 12_000 + (seed % 43_000).toInt()
    return "${formatCountInTenThousands(learners)} 人在学"
}

private fun formatCountInTenThousands(value: Int): String {
    if (value < 10_000) return value.toString()
    val scaled = value / 10_000.0
    val text = String.format(Locale.US, "%.1f", scaled)
    return text.removeSuffix(".0") + "w"
}
