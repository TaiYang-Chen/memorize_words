package com.chen.memorizewords.data.local.room.model.study.favorites

import com.chen.memorizewords.domain.model.study.favorites.WordFavorites
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun FavoriteWordRow.toDomain(): WordFavorites {
    return WordFavorites(
        id = 0,
        wordId = wordId,
        word = word,
        definitions = definitions,
        phonetic = phonetic,
        addedDate = addedDate
    )
}

fun WordFavorites.toEntity(addedAt: Long = System.currentTimeMillis()): WordFavoriteEntity {
    return WordFavoriteEntity(
        wordId = wordId,
        addedAt = addedAt
    )
}

internal fun parseFavoriteAddedAt(addedDate: String, fallback: Long = System.currentTimeMillis()): Long {
    if (addedDate.isBlank()) return fallback
    return runCatching {
        SimpleDateFormat(FAVORITE_DATE_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getDefault()
            isLenient = false
        }.parse(addedDate)?.time
    }.getOrNull() ?: fallback
}

internal fun formatFavoriteAddedDate(addedAt: Long): String {
    return SimpleDateFormat(FAVORITE_DATE_PATTERN, Locale.US).apply {
        timeZone = TimeZone.getDefault()
        isLenient = false
    }.format(addedAt)
}

private const val FAVORITE_DATE_PATTERN = "yyyy-MM-dd"
