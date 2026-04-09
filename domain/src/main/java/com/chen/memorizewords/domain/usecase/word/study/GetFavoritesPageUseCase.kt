package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.model.common.PageSlice
import com.chen.memorizewords.domain.model.study.favorites.WordFavorites
import com.chen.memorizewords.domain.repository.word.FavoritesRepository
import javax.inject.Inject

class GetFavoritesPageUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) {
    suspend operator fun invoke(pageIndex: Int, pageSize: Int): PageSlice<WordFavorites> {
        return favoritesRepository.getFavoritesPage(
            pageIndex = pageIndex.coerceAtLeast(0),
            pageSize = pageSize.coerceAtLeast(1)
        )
    }
}
