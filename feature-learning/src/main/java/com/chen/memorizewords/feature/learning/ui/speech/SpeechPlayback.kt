package com.chen.memorizewords.feature.learning.ui.speech

import android.media.MediaPlayer
import com.chen.memorizewords.speech.api.SpeechAudioOutput
import com.chen.memorizewords.speech.api.SpeechAudioSuccess
import com.chen.memorizewords.speech.api.SpeechResult
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

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

suspend fun playSpeechOutputSuspending(output: SpeechAudioOutput) {
    suspendCancellableCoroutine<Unit> { continuation ->
        val player = MediaPlayer()
        val started = player.prepareSpeechOutputAsync(
            output = output,
            onPrepared = {
                it.setOnCompletionListener {
                    runCatching { player.release() }
                    if (continuation.isActive) continuation.resume(Unit)
                }
                it.start()
            },
            onError = {
                runCatching { player.release() }
                if (continuation.isActive) continuation.resume(Unit)
            }
        )
        if (!started && continuation.isActive) {
            continuation.resume(Unit)
        }
        continuation.invokeOnCancellation { runCatching { player.release() } }
    }
}

fun speechOutputFileOrNull(output: SpeechAudioOutput?): File? {
    return (output as? SpeechAudioOutput.FileOutput)?.let { File(it.filePath) }
}
