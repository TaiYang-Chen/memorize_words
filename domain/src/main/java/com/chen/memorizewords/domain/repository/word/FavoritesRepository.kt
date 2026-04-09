package com.chen.memorizewords.domain.repository.word

import com.chen.memorizewords.domain.model.common.PageSlice
import com.chen.memorizewords.domain.model.study.favorites.WordFavorites

interface FavoritesRepository {
    suspend fun addFavorite(favorites: WordFavorites)
    suspend fun removeFavorite(wordId: Long)
    suspend fun isFavorite(wordId: Long): Boolean
    suspend fun getFavoritesPage(pageIndex: Int, pageSize: Int): PageSlice<WordFavorites>
}
