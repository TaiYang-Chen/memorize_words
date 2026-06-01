package com.chen.memorizewords.domain.practice.usecase

import com.chen.memorizewords.domain.practice.speech.PracticeSpeechSynthesizer
import com.chen.memorizewords.domain.practice.speech.SpeechResult
import com.chen.memorizewords.domain.practice.speech.SpeechTask
import javax.inject.Inject

class SynthesizeSpeechUseCase @Inject constructor(
    private val synthesizer: PracticeSpeechSynthesizer
) {
    suspend operator fun invoke(task: SpeechTask): SpeechResult {
        return synthesizer.synthesize(task)
    }
}
