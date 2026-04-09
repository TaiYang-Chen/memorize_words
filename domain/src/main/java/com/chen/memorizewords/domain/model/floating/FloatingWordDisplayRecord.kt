package com.chen.memorizewords.domain.model.floating

data class FloatingWordDisplayRecord(
    val date: String,
    val displayCount: Int,
    val wordIds: List<Long>,
    val updatedAt: Long
)
