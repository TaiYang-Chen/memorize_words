package com.chen.memorizewords.feature.home.ui.home

import com.chen.memorizewords.domain.orchestrator.learning.LearningSessionFacade
import com.chen.memorizewords.domain.repository.WordOrderType

internal class HomeLearningLauncher(
    private val learningSessionFacade: LearningSessionFacade
) {

    suspend fun createNewRoute(
        bookId: Long,
        count: Int,
        orderType: WordOrderType
    ): HomeViewModel.Route.ToLearning? {
        return createLearningRoute(
            learningSessionFacade.createNewSessionRequest(
                bookId = bookId,
                count = count,
                orderType = orderType
            )
        )
    }

    suspend fun createReviewRoute(
        bookId: Long,
        count: Int,
        orderType: WordOrderType
    ): HomeViewModel.Route.ToLearning? {
        return createLearningRoute(
            learningSessionFacade.createReviewSessionRequest(
                bookId = bookId,
                count = count,
                orderType = orderType
            )
        )
    }
}
