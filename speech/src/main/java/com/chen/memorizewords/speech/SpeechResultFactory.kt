package com.chen.memorizewords.speech

import com.chen.memorizewords.speech.api.DefaultSpeechFailureResult
import com.chen.memorizewords.speech.api.SpeechFailure
import com.chen.memorizewords.speech.api.SpeechFailureResult
import com.chen.memorizewords.speech.api.SpeechProviderType

internal fun failureResult(
    provider: SpeechProviderType,
    traceId: String,
    failure: SpeechFailure,
    message: String? = null,
    causeCode: String? = null
): SpeechFailureResult {
    return DefaultSpeechFailureResult(
        provider = provider,
        traceId = traceId,
        failure = failure,
        causeCode = causeCode,
        message = message
    )
}
