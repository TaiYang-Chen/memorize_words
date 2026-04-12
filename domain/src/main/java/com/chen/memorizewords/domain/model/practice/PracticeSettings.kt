package com.chen.memorizewords.domain.model.practice

data class PracticeSettings(
    val selectedBookId: Long = 0L,
    val intervalSeconds: Int = 3,
    val loopEnabled: Boolean = true,
    val showPhonetic: Boolean = true,
    val showMeaning: Boolean = false,
    val playbackMode: AudioLoopPlaybackMode = AudioLoopPlaybackMode.WORD_ONLY,
    val playTimes: Int = 1
)
