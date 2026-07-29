package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.repository.record.DailyStudyRepository
import javax.inject.Inject

class MakeUpCheckInUseCase @Inject constructor(
    private val dailyStudyRepository: DailyStudyRepository
) {
    suspend operator fun invoke(date: String): Result<CheckInRecord> {
        return dailyStudyRepository.makeUpCheckIn(date)
    }
}
