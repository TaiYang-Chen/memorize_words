package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.repository.word.FavoritesRepository
import javax.inject.Inject

class IsFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) {
    suspend operator fun invoke(
        wordId: Long
    ): Boolean {
        return favoritesRepository.isFavorite(wordId)
    }
}