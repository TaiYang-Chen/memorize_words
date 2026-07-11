package com.chen.memorizewords.domain.practice.usecase

import com.chen.memorizewords.domain.practice.speech.ShadowingEvaluator
import com.chen.memorizewords.domain.practice.speech.ShadowingEvaluationResult
import com.chen.memorizewords.domain.practice.speech.SpeechResult
import com.chen.memorizewords.domain.practice.speech.SpeechTask
import com.chen.memorizewords.domain.practice.usage.PracticeUsageRepository
import javax.inject.Inject

class EvaluateShadowingUseCase @Inject constructor(
    private val shadowingEvaluator: ShadowingEvaluator,
    private val practiceUsageRepository: PracticeUsageRepository
) {
    suspend operator fun invoke(task: SpeechTask.EvaluateShadowing): SpeechResult {
        return shadowingEvaluator.evaluate(task).also { result ->
            if (result is ShadowingEvaluationResult) {
                result.evaluationUsage?.let { practiceUsageRepository.updateEvaluationUsage(it) }
            }
        }
    }
}
