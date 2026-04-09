package com.chen.memorizewords.domain.usecase.practice

import com.chen.memorizewords.domain.repository.practice.PracticeRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPracticeTotalDurationUseCase @Inject constructor(
    private val practiceRecordRepository: PracticeRecordRepository
) {
    operator fun invoke(): Flow<Long> {
        return practiceRecordRepository.getPracticeTotalDurationMs()
    }
}
