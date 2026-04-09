package com.chen.memorizewords.domain.repository.floating

import com.chen.memorizewords.domain.model.floating.FloatingWordDisplayRecord

interface FloatingWordDisplayRecordRepository {
    suspend fun recordDisplay(wordId: Long)
    suspend fun getRecordByDate(date: String): FloatingWordDisplayRecord?
}
