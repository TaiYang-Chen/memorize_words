package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.service.FloatingReviewFacade
import com.chen.memorizewords.domain.floating.service.FloatingWordCardContent
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.study.usecase.word.study.IsFavoriteUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.ToggleFavoriteUseCase
import com.chen.memorizewords.domain.word.model.word.Word
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class FloatingWordController @Inject constructor(
    private val floatingReviewFacade: FloatingReviewFacade,
    private val isFavoriteUseCase: IsFavoriteUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) {

    fun observeSettings(): Flow<FloatingWordSettings> = floatingReviewFacade.observeSettings()

    suspend fun getSettings(): FloatingWordSettings = floatingReviewFacade.getSettings()

    suspend fun updateBallPosition(x: Int, y: Int, dockState: FloatingDockState?) {
        floatingReviewFacade.updateBallPosition(x, y, dockState)
    }

    suspend fun recordDisplay(wordId: Long) {
        floatingReviewFacade.recordDisplay(wordId)
    }

    suspend fun loadWords(settings: FloatingWordSettings): List<Word> =
        floatingReviewFacade.loadWords(settings)

    suspend fun loadCardContent(
        word: Word,
        settings: FloatingWordSettings
    ): FloatingWordCardContent = floatingReviewFacade.loadCardContent(word, settings)

    suspend fun isFavorite(wordId: Long): Boolean = isFavoriteUseCase(wordId)

    suspend fun toggleFavorite(word: Word) {
        toggleFavoriteUseCase(word)
    }
}
