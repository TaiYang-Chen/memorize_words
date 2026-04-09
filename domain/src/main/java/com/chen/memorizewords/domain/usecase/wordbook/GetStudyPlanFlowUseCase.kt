package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.model.study.StudyPlan
import com.chen.memorizewords.domain.repository.StudyPlanRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetStudyPlanFlowUseCase @Inject constructor(
    private val repository: StudyPlanRepository
) {
    operator fun invoke(): Flow<StudyPlan> = repository.getStudyPlanFlow()
}
