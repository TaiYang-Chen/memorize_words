package com.chen.memorizewords.speech

import com.chen.memorizewords.speech.api.SpeechProviderSelector
import com.chen.memorizewords.speech.api.SpeechProviderType
import com.chen.memorizewords.speech.api.SpeechTask
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSpeechProviderSelector @Inject constructor(
    private val runtimeConfig: SpeechRuntimeConfig
) : SpeechProviderSelector {

    override fun select(task: SpeechTask): SpeechProviderType {
        return when (task) {
            is SpeechTask.SynthesizeWord -> runtimeConfig.wordTtsProvider
            is SpeechTask.SynthesizeSentence -> runtimeConfig.sentenceTtsProvider
            is SpeechTask.EvaluateShadowing -> runtimeConfig.evaluationProvider
        }
    }
}
