package com.chen.memorizewords.domain.model.practice

data class PracticeSettings(
    val selectedBookId: Long = 0L,
    val intervalSeconds: Int = 3,
    val loopEnabled: Boolean = true,
    val playWordSpelling: Boolean = true,
    val playChineseMeaning: Boolean = false
)
