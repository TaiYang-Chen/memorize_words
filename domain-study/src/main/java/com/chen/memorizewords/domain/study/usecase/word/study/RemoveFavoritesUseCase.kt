package com.chen.memorizewords.domain.study.usecase.word.study

import javax.inject.Inject

class RemoveFavoritesUseCase @Inject constructor(
    private val removeFavoriteUseCase: RemoveFavoriteUseCase
) {
    suspend operator fun invoke(wordIds: Collection<Long>) {
        wordIds
            .asSequence()
            .filter { it > 0L }
            .distinct()
            .forEach { removeFavoriteUseCase(it) }
    }
}
