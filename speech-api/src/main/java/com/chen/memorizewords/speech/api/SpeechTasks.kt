package com.chen.memorizewords.speech.api

sealed interface SpeechTask {
    val requiredCapability: SpeechCapability

    data class SynthesizeWord(
        val text: String,
        val locale: String = "en-US",
        val voice: String = "default",
        val audioFormat: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ) : SpeechTask {
        override val requiredCapability: SpeechCapability = SpeechCapability.WORD_TTS
    }

    data class SynthesizeSentence(
        val text: String,
        val locale: String = "en-US",
        val voice: String = "default",
        val audioFormat: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ) : SpeechTask {
        override val requiredCapability: SpeechCapability = SpeechCapability.SENTENCE_TTS
    }

    data class EvaluateShadowing(
        val referenceText: String,
        val audioInput: SpeechAudioInput,
        val locale: String = "en-US"
    ) : SpeechTask {
        override val requiredCapability: SpeechCapability = SpeechCapability.SHADOWING_EVALUATION
    }
}
