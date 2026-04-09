package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.repository.StudyPlanRepository
import javax.inject.Inject

class SaveStudyCountUseCase @Inject constructor(
    private val repository: StudyPlanRepository
) {
    suspend operator fun invoke(dailyNewCount: Int, dailyReviewCount: Int) {
        repository.saveStudyCount(dailyNewCount, dailyReviewCount)
    }
}
