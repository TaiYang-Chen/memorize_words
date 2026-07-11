package com.chen.memorizewords.domain.practice.usage

import javax.inject.Inject

class ObservePracticeUsageUseCase @Inject constructor(private val repository: PracticeUsageRepository) {
    operator fun invoke() = repository.observe()
}

class RefreshPracticeUsageUseCase @Inject constructor(private val repository: PracticeUsageRepository) {
    suspend operator fun invoke() = repository.refresh()
}

class ClearPracticeUsageUseCase @Inject constructor(private val repository: PracticeUsageRepository) {
    suspend operator fun invoke() = repository.clear()
}
