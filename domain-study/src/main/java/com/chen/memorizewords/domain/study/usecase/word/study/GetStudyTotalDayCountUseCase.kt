package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.repository.record.StudyRecordQuery
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStudyTotalDayCountUseCase @Inject constructor(
    private val studyRecordQuery: StudyRecordQuery
) {
    operator fun invoke(): Flow<Int> {
        return studyRecordQuery.getStudyTotalDayCount()
    }
}
