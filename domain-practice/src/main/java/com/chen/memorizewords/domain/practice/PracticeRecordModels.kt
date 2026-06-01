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
    WORD_WITH_EXAMPLE
}

data class PracticeSettings(
    val selectedBookId: Long = 0L,
    val intervalSeconds: Int = 3,
    val loopEnabled: Boolean = true,
    val showPhonetic: Boolean = true,
    val showMeaning: Boolean = false,
    val playbackMode: AudioLoopPlaybackMode = AudioLoopPlaybackMode.WORD_ONLY,
    val playTimes: Int = 1
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
