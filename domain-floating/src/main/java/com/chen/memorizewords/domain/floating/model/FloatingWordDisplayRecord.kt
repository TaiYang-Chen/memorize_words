package com.chen.memorizewords.domain.floating.model
data class FloatingWordDisplayRecord(
    val date: String,
    val displayCount: Int,
    val wordIds: List<Long>,
    val updatedAt: Long
)
