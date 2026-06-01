package com.chen.memorizewords.feature.home.ui.home

import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.domain.study.orchestrator.learning.LearningSessionFacade
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType

internal class HomeLearningLauncher(
    private val learningSessionFacade: LearningSessionFacade
) {

    suspend fun createNewRoute(
        bookId: Long,
        count: Int,
        orderType: WordOrderType
    ): AppRoute.Learning? {
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
    ): AppRoute.Learning? {
        return createLearningRoute(
            learningSessionFacade.createReviewSessionRequest(
                bookId = bookId,
                count = count,
                orderType = orderType
            )
        )
    }
}
