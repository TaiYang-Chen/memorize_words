package com.chen.memorizewords.feature.learning.ui.practice.listening.audio

import android.media.MediaPlayer
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import com.chen.memorizewords.speech.api.SpeechAudioOutput

internal class ListeningAudioPlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun play(
        output: SpeechAudioOutput,
        onUnavailable: () -> Unit
    ) {
        release()
        val player = MediaPlayer()
        val prepared = player.prepareSpeechOutputAsync(
            output = output,
            onPrepared = {
                it.setOnCompletionListener { release() }
                it.start()
            },
            onError = {
                release()
                onUnavailable()
            }
        )
        if (prepared) {
            mediaPlayer = player
        }
    }

    fun release() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        runCatching {
            runCatching { player.stop() }
            player.release()
        }
    }
}
