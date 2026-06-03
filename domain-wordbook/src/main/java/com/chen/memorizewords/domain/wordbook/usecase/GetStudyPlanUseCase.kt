package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import javax.inject.Inject

class GetStudyPlanUseCase @Inject constructor(
    private val repository: StudyPlanRepository
) {
    suspend operator fun invoke(): StudyPlan? = repository.getStudyPlan()
}
