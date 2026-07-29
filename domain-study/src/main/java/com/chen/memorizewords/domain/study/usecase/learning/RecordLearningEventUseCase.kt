package com.chen.memorizewords.domain.study.usecase.learning

import com.chen.memorizewords.domain.study.model.learning.DailyProgressTransition
import com.chen.memorizewords.domain.study.model.learning.LearningActivityCommitResult
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventCommand
import com.chen.memorizewords.domain.study.model.learning.createsDailyStudyRecord
import com.chen.memorizewords.domain.study.repository.learning.LearningCommandPort
import com.chen.memorizewords.domain.study.service.DailyProgressWriteCoordinator
import com.chen.memorizewords.domain.study.service.DailyStudyProjectionCoordinator
import com.chen.memorizewords.domain.study.service.toDailyPlanTargets
import com.chen.memorizewords.domain.sync.usecase.TriggerSyncDrainUseCase
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import javax.inject.Inject

class RecordLearningEventUseCase @Inject constructor(
    private val learningCommandPort: LearningCommandPort,
    private val studyPlanRepository: StudyPlanRepository,
    private val writeCoordinator: DailyProgressWriteCoordinator,
    private val projectionCoordinator: DailyStudyProjectionCoordinator,
    private val triggerSyncDrain: TriggerSyncDrainUseCase
) {
    suspend operator fun invoke(command: RecordLearningEventCommand): LearningActivityCommitResult {
        val committed = writeCoordinator.withWrite {
            val enrichedCommand = if (command.action.createsDailyStudyRecord) {
                val targets = (studyPlanRepository.getStudyPlan() ?: StudyPlan()).toDailyPlanTargets()
                command.copy(
                    dailyNewTarget = targets.newTarget,
                    dailyReviewTarget = targets.reviewTarget
                )
            } else {
                command
            }
            val event = learningCommandPort.record(enrichedCommand)
            val projection = if (command.action.createsDailyStudyRecord) {
                projectionCoordinator.projectEventLocked(event.clientEventId)
            } else {
                DailyProgressTransition.NotEligible(command.businessDate)
            }
            LearningActivityCommitResult(event, projection)
        }
        runCatching { triggerSyncDrain() }
        return committed
    }
}
