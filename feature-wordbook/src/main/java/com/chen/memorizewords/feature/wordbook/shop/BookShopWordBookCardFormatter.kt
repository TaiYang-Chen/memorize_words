package com.chen.memorizewords.feature.wordbook.shop

import android.content.Context
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.feature.wordbook.R
import java.text.NumberFormat

internal fun formatBookShopWordCount(totalWords: Int): String {
    return "${NumberFormat.getIntegerInstance().format(totalWords)} 词"
}

internal fun formatBookShopMeta(context: Context, wordBook: WordBook): String {
    val visibility = context.getString(
        if (wordBook.isPublic) {
            R.string.module_wordbook_public
        } else {
            R.string.module_wordbook_private
        }
    )
    return formatBookShopMeta(wordBook.category, visibility)
}

internal fun formatBookShopMeta(category: String, visibility: String): String {
    val trimmedCategory = category.trim()
    return if (trimmedCategory.isEmpty()) {
        visibility
    } else {
        "$trimmedCategory · $visibility"
    }
}
