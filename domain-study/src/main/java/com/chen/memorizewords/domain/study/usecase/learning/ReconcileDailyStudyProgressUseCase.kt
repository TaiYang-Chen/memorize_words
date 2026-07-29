package com.chen.memorizewords.domain.study.usecase.learning

import com.chen.memorizewords.domain.study.model.learning.DailyProgressTransition
import com.chen.memorizewords.domain.study.service.DailyStudyProjectionCoordinator
import javax.inject.Inject

class ReconcileDailyStudyProgressUseCase @Inject constructor(
    private val coordinator: DailyStudyProjectionCoordinator
) {
    suspend operator fun invoke(): DailyProgressTransition = coordinator.reconcileCurrentDay()
}
