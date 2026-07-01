package com.chen.memorizewords.feature.learning.ui.practice.audioLoop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioLoopPlaybackStore {
    private val _queue = MutableStateFlow<AudioLoopServiceQueue?>(null)
    val queue: StateFlow<AudioLoopServiceQueue?> = _queue.asStateFlow()

    private val _state = MutableStateFlow(AudioLoopServiceState())
    val state: StateFlow<AudioLoopServiceState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun setQueue(queue: AudioLoopServiceQueue, resetPosition: Boolean) {
        val previousState = _state.value
        val sameEntries = previousState.entries.map { it.id } == queue.entries.map { it.id }
        val keepPlaybackPosition = !resetPosition && sameEntries
        val nextIndex = if (keepPlaybackPosition) {
            previousState.currentIndex.coerceIn(0, queue.entries.lastIndex.coerceAtLeast(0))
        } else {
            0
        }
        val nextRepeat = if (keepPlaybackPosition) {
            previousState.currentRepeat
        } else {
            0
        }
        _queue.value = queue
        _state.value = AudioLoopServiceState(
            playerState = if (queue.entries.isEmpty()) {
                AudioLoopServicePlayerState.EMPTY
            } else if (keepPlaybackPosition) {
                previousState.playerState
            } else {
                AudioLoopServicePlayerState.WAITING
            },
            entries = queue.entries,
            currentIndex = nextIndex,
            currentRepeat = nextRepeat,
            totalRepeats = queue.settings.playTimes.coerceAtLeast(1),
            completedIds = if (keepPlaybackPosition) {
                previousState.completedIds
            } else {
                emptySet()
            },
            failedIds = if (keepPlaybackPosition) {
                previousState.failedIds
            } else {
                emptySet()
            }
        )
    }

    fun updateState(reducer: (AudioLoopServiceState) -> AudioLoopServiceState) {
        _state.value = reducer(_state.value)
    }

    fun clear() {
        _queue.value = null
        _state.value = AudioLoopServiceState()
    }

    fun message(text: String) {
        _messages.tryEmit(text)
        updateState { it.copy(lastMessage = text) }
    }
}
