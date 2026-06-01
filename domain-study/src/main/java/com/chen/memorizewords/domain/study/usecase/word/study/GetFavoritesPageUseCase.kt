package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.repository.word.FavoritesRepository
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
