package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetStudyPlanFlowUseCase @Inject constructor(
    private val repository: StudyPlanRepository
) {
    operator fun invoke(): Flow<StudyPlan?> = repository.getStudyPlanFlow()
}
