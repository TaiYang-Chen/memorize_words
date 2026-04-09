package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.model.study.record.DailyStudyDetail
import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetDayStudyDetailUseCase @Inject constructor(
    private val learningRecordRepository: LearningRecordRepository
) {
    operator fun invoke(date: String): Flow<DailyStudyDetail> {
        return combine(
            learningRecordRepository.getDailyStudyWordRecords(date),
            learningRecordRepository.getDailyStudySummary(date),
            learningRecordRepository.getDayCheckInDetail(date)
        ) { records, summary, checkInDetail ->
            val newWords = records.filter { it.isNewWord }
            val reviewWords = records.filterNot { it.isNewWord }
            val newCount = newWords.size
            val reviewCount = reviewWords.size
            DailyStudyDetail(
                date = date,
                newCount = newCount,
                reviewCount = reviewCount,
                durationMs = summary.durationMs,
                hasStudy = newCount > 0 || reviewCount > 0 || summary.durationMs > 0L,
                isNewPlanCompleted = summary.isNewPlanCompleted,
                isReviewPlanCompleted = summary.isReviewPlanCompleted,
                newWords = newWords,
                reviewWords = reviewWords,
                checkInRecord = checkInDetail.record,
                canMakeUp = checkInDetail.canMakeUp,
                availableMakeupCardCount = checkInDetail.availableMakeupCardCount
            )
        }
    }
}
