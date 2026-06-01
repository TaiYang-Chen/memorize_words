package com.chen.memorizewords.domain.floating.repository
import com.chen.memorizewords.domain.floating.model.FloatingWordDisplayRecord

interface FloatingWordDisplayRecordRepository {
    suspend fun recordDisplay(wordId: Long)
    suspend fun getRecordByDate(date: String): FloatingWordDisplayRecord?
}
