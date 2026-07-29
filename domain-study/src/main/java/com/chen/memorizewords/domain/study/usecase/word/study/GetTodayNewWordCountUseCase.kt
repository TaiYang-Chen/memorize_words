package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.repository.record.BusinessDateProvider
import com.chen.memorizewords.domain.study.repository.record.StudyRecordQuery
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTodayNewWordCountUseCase @Inject constructor(
    private val studyRecordQuery: StudyRecordQuery,
    private val businessDateProvider: BusinessDateProvider
) {
    operator fun invoke(): Flow<Int> {
        return studyRecordQuery.getNewWordCount(businessDateProvider.currentBusinessDate())
    }
}
