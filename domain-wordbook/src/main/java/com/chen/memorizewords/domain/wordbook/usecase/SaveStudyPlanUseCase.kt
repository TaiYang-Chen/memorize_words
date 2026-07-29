package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.domain.study.service.DailyProgressWriteCoordinator
import com.chen.memorizewords.domain.study.service.DailyStudyProjectionCoordinator
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import javax.inject.Inject

class SaveStudyPlanUseCase @Inject constructor(
    private val repository: StudyPlanRepository,
    private val writeCoordinator: DailyProgressWriteCoordinator,
    private val projectionCoordinator: DailyStudyProjectionCoordinator
) {
    suspend operator fun invoke(studyPlan: StudyPlan) {
        writeCoordinator.withWrite {
            repository.saveStudyPlan(studyPlan)
            projectionCoordinator.reconcileCurrentDayLocked()
        }
    }
}
