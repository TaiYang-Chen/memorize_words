package com.chen.memorizewords.feature.learning.ui.speech

import android.media.MediaPlayer
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput
import com.chen.memorizewords.domain.practice.speech.SpeechAudioSuccess
import com.chen.memorizewords.domain.practice.speech.SpeechResult
import java.io.File

fun SpeechResult.audioOutputOrNull(): SpeechAudioOutput? {
    return (this as? SpeechAudioSuccess)?.audioOutput
}

fun MediaPlayer.setSpeechDataSource(output: SpeechAudioOutput) {
    when (output) {
        is SpeechAudioOutput.FileOutput -> setDataSource(output.filePath)
        is SpeechAudioOutput.UrlOutput -> setDataSource(output.url)
        is SpeechAudioOutput.StreamOutput -> error("Stream audio output is not supported yet.")
    }
}

fun MediaPlayer.prepareSpeechOutputAsync(
    output: SpeechAudioOutput,
    onPrepared: (MediaPlayer) -> Unit = { it.start() },
    onError: (MediaPlayer) -> Unit = { runCatching { it.release() } }
): Boolean {
    return runCatching {
        setSpeechDataSource(output)
        setOnPreparedListener { player -> onPrepared(player) }
        setOnErrorListener { player, _, _ ->
            onError(player)
            true
        }
        prepareAsync()
        true
    }.getOrElse {
        onError(this)
        false
    }
}

fun speechOutputFileOrNull(output: SpeechAudioOutput?): File? {
    return (output as? SpeechAudioOutput.FileOutput)?.let { File(it.filePath) }
}
