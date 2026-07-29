package com.chen.memorizewords.domain.study.repository

import com.chen.memorizewords.domain.study.model.favorites.WordFavorites

interface FavoritesSnapshotPort {
    suspend fun overwriteFavoritesFromRemote(favorites: List<WordFavorites>)
}
