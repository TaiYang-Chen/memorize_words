package com.chen.memorizewords.domain.study.repository.word
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites

interface FavoritesRepository {
    suspend fun addFavorite(favorites: WordFavorites)
    suspend fun removeFavorite(wordId: Long)
    suspend fun isFavorite(wordId: Long): Boolean
    suspend fun getAllFavoriteWordIds(): List<Long>
    suspend fun getFavoritesPage(pageIndex: Int, pageSize: Int): PageSlice<WordFavorites>
}
