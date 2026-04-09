package com.chen.memorizewords.domain.usecase.practice

import com.chen.memorizewords.speech.api.SpeechResult
import com.chen.memorizewords.speech.api.SpeechService
import com.chen.memorizewords.speech.api.SpeechTask
import javax.inject.Inject

class EvaluateShadowingUseCase @Inject constructor(
    private val speechService: SpeechService
) {
    suspend operator fun invoke(task: SpeechTask.EvaluateShadowing): SpeechResult {
        return speechService.execute(task)
    }
}
