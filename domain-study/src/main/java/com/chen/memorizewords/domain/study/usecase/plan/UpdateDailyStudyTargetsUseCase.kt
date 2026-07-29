package com.chen.memorizewords.domain.study.usecase.plan

import com.chen.memorizewords.domain.study.model.learning.DailyProgressTransition
import com.chen.memorizewords.domain.study.service.DailyProgressWriteCoordinator
import com.chen.memorizewords.domain.study.service.DailyStudyProjectionCoordinator
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import javax.inject.Inject

class UpdateDailyStudyTargetsUseCase @Inject constructor(
    private val studyPlanRepository: StudyPlanRepository,
    private val writeCoordinator: DailyProgressWriteCoordinator,
    private val projectionCoordinator: DailyStudyProjectionCoordinator
) {
    suspend operator fun invoke(
        dailyNewCount: Int,
        dailyReviewCount: Int
    ): DailyProgressTransition = writeCoordinator.withWrite {
        studyPlanRepository.saveStudyCount(
            dailyNewCount = dailyNewCount.coerceAtLeast(0),
            dailyReviewCount = dailyReviewCount.coerceAtLeast(0)
        )
        projectionCoordinator.reconcileCurrentDayLocked()
    }
}
