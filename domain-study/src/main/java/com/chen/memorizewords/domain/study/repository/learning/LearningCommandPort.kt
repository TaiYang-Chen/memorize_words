package com.chen.memorizewords.domain.study.repository.learning

import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventCommand
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventResult

interface LearningCommandPort {
    suspend fun record(command: RecordLearningEventCommand): RecordLearningEventResult
}
