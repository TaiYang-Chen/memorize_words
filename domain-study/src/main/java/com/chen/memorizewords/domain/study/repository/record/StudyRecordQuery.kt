package com.chen.memorizewords.domain.study.repository.record

import com.chen.memorizewords.domain.study.model.learning.DailyWordCounts
import com.chen.memorizewords.domain.study.model.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import kotlinx.coroutines.flow.Flow

interface StudyRecordQuery {
    fun getStudyTotalDayCount(): Flow<Int>
    fun getNewWordCount(date: String): Flow<Int>
    fun getReviewWordCount(date: String): Flow<Int>
    suspend fun getWordCounts(date: String): DailyWordCounts
    fun getDailyWordStats(startDate: String, endDate: String): Flow<List<DailyWordStats>>
    fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecord>>
    fun observeStudyDatesBetween(startDate: String, endDate: String): Flow<List<String>>
}
