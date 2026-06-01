package com.chen.memorizewords.domain.practice.usecase

import com.chen.memorizewords.domain.practice.speech.ShadowingEvaluator
import com.chen.memorizewords.domain.practice.speech.SpeechResult
import com.chen.memorizewords.domain.practice.speech.SpeechTask
import javax.inject.Inject

class EvaluateShadowingUseCase @Inject constructor(
    private val shadowingEvaluator: ShadowingEvaluator
) {
    suspend operator fun invoke(task: SpeechTask.EvaluateShadowing): SpeechResult {
        return shadowingEvaluator.evaluate(task)
    }
}
