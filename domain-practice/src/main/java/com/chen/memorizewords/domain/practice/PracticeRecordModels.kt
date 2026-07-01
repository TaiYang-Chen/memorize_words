package com.chen.memorizewords.domain.practice
data class PracticeDailyDurationStats(
    val date: String,
    val durationMs: Long
)

enum class PracticeMode {
    LISTENING,
    SHADOWING,
    SPELLING,
    AUDIO_LOOP,
    EXAM
}

enum class PracticeEntryType {
    SELF,
    RANDOM
}

enum class AudioLoopPlaybackMode {
    WORD_ONLY,
    WORD_WITH_EXAMPLE,
    WORD_WITH_MEANING,
    DICTATION,
    FULL_DETAIL
}

enum class AudioLoopPlayOrder {
    SEQUENTIAL,
    RANDOM
}

data class PracticeSettings(
    val selectedBookId: Long = 0L,
    val intervalSeconds: Int = 3,
    val loopEnabled: Boolean = true,
    val showPhonetic: Boolean = true,
    val showMeaning: Boolean = false,
    val playbackMode: AudioLoopPlaybackMode = AudioLoopPlaybackMode.WORD_ONLY,
    val playTimes: Int = 1,
    val wordRepeatTimes: Int = 1,
    val exampleRepeatTimes: Int = 1,
    val dictationPauseSeconds: Int = 5,
    val revealDelaySeconds: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val timedStopMinutes: Int = 0,
    val keepScreenOn: Boolean = false,
    val playOrder: AudioLoopPlayOrder = AudioLoopPlayOrder.SEQUENTIAL
)

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
