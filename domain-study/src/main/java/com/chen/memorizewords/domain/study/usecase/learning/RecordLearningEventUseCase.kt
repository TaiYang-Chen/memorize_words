package com.chen.memorizewords.domain.study.usecase.learning

import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventCommand
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventResult
import com.chen.memorizewords.domain.study.repository.learning.LearningCommandPort
import com.chen.memorizewords.domain.sync.usecase.TriggerSyncDrainUseCase
import javax.inject.Inject

class RecordLearningEventUseCase @Inject constructor(
    private val learningCommandPort: LearningCommandPort,
    private val triggerSyncDrain: TriggerSyncDrainUseCase
) {
    suspend operator fun invoke(command: RecordLearningEventCommand): RecordLearningEventResult {
        val result = learningCommandPort.record(command)
        runCatching { triggerSyncDrain() }
        return result
    }
}
