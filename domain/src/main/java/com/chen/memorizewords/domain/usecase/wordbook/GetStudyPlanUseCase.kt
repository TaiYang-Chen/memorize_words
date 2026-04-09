package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.model.study.StudyPlan
import com.chen.memorizewords.domain.repository.StudyPlanRepository
import javax.inject.Inject

class GetStudyPlanUseCase @Inject constructor(
    private val repository: StudyPlanRepository
) {
    suspend operator fun invoke(): StudyPlan = repository.getStudyPlan()
}
