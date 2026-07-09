package com.chen.memorizewords.data.wordbook.remoteapi.dto.wordstate

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WordStateDto(
    val wordId: Long,
    val bookId: Long,
    val totalLearnCount: Int,
    val lastLearnedAtMs: Long,
    val nextReviewAtMs: Long,
    val masteryLevel: Int,
    val userStatus: Int,
    val repetition: Int,
    val interval: Long,
    val efactor: Double
)
