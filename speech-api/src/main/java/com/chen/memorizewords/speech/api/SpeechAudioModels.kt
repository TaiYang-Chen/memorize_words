package com.chen.memorizewords.speech.api

sealed interface SpeechAudioInput {
    data class FileInput(
        val filePath: String,
        val format: SpeechAudioFormat = SpeechAudioFormat.defaultInput()
    ) : SpeechAudioInput

    data class ByteArrayInput(
        val bytes: ByteArray,
        val format: SpeechAudioFormat = SpeechAudioFormat.defaultInput()
    ) : SpeechAudioInput

    data class StreamInput(
        val streamId: String,
        val format: SpeechAudioFormat = SpeechAudioFormat.defaultInput()
    ) : SpeechAudioInput
}

sealed interface SpeechAudioOutput {
    val format: SpeechAudioFormat

    data class FileOutput(
        val filePath: String,
        override val format: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ) : SpeechAudioOutput

    data class UrlOutput(
        val url: String,
        override val format: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ) : SpeechAudioOutput

    data class StreamOutput(
        val streamId: String,
        override val format: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ) : SpeechAudioOutput
}

data class SpeechAudioFormat(
    val mimeType: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val encoding: String
) {
    companion object {
        fun defaultOutput(): SpeechAudioFormat {
            return SpeechAudioFormat(
                mimeType = "audio/mpeg",
                sampleRateHz = 16000,
                channelCount = 1,
                encoding = "mp3"
            )
        }

        fun defaultInput(): SpeechAudioFormat {
            return SpeechAudioFormat(
                mimeType = "audio/mp4",
                sampleRateHz = 16000,
                channelCount = 1,
                encoding = "aac"
            )
        }
    }
}
