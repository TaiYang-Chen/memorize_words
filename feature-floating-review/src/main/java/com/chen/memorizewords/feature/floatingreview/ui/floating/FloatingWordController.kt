package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.domain.model.floating.FloatingDockState
import com.chen.memorizewords.domain.service.floating.FloatingReviewFacade
import com.chen.memorizewords.domain.service.floating.FloatingWordCardContent
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.domain.model.words.word.Word
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class FloatingWordController @Inject constructor(
    private val floatingReviewFacade: FloatingReviewFacade
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
}
