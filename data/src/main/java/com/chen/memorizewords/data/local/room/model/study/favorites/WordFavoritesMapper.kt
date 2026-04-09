package com.chen.memorizewords.data.local.room.model.study.favorites

import com.chen.memorizewords.domain.model.study.favorites.WordFavorites

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

fun WordFavorites.toEntity(): WordFavoriteEntity {
    return WordFavoriteEntity(
        wordId = wordId,
        addedDate = addedDate
    )
}
