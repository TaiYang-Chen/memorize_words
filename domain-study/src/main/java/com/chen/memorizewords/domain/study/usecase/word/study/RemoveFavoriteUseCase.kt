package com.chen.memorizewords.domain.study.usecase.word.study

import com.chen.memorizewords.domain.study.repository.word.FavoritesRepository
import javax.inject.Inject

class RemoveFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) {
    suspend operator fun invoke(wordId: Long) {
        if (wordId <= 0L) return
        favoritesRepository.removeFavorite(wordId)
    }
}
