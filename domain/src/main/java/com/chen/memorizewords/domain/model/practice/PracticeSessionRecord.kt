package com.chen.memorizewords.domain.model.practice

data class PracticeSessionRecord(
    val id: Long,
    val date: String,
    val mode: PracticeMode,
    val entryType: PracticeEntryType,
    val entryCount: Int,
    val durationMs: Long,
    val createdAt: Long,
    val wordIds: List<Long>,
    val questionCount: Int = 0,
    val completedCount: Int = 0,
    val correctCount: Int = 0,
    val submitCount: Int = 0
)
