package com.chen.memorizewords.feature.learning.ui.practice.audioLoop

import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeSettings

enum class AudioLoopCueKind {
    WORD,
    SENTENCE,
    SILENCE
}

enum class AudioLoopServicePlayerState {
    EMPTY,
    WAITING,
    PREPARING,
    PLAYING,
    PAUSED,
    STOPPED
}

data class AudioLoopServiceEntry(
    val id: Long,
    val word: String,
    val phonetic: String,
    val phoneticUs: String,
    val phoneticUk: String,
    val speechLocale: String,
    val meaning: String,
    val exampleSentence: String,
    val exampleTranslation: String
)

data class AudioLoopServiceCue(
    val key: String,
    val kind: AudioLoopCueKind,
    val text: String,
    val locale: String = "en-US",
    val delayAfterMs: Long = 0L
)

data class AudioLoopServiceQueue(
    val entries: List<AudioLoopServiceEntry>,
    val settings: PracticeSettings,
    val entryType: PracticeEntryType,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class AudioLoopServiceState(
    val playerState: AudioLoopServicePlayerState = AudioLoopServicePlayerState.EMPTY,
    val entries: List<AudioLoopServiceEntry> = emptyList(),
    val currentIndex: Int = 0,
    val currentRepeat: Int = 0,
    val totalRepeats: Int = 0,
    val completedIds: Set<Long> = emptySet(),
    val failedIds: Set<Long> = emptySet(),
    val lastMessage: String? = null
) {
    val currentEntry: AudioLoopServiceEntry?
        get() = entries.getOrNull(currentIndex)
}
