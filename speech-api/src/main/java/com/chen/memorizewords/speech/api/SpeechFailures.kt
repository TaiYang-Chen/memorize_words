package com.chen.memorizewords.speech.api

sealed class SpeechFailure {
    data class AuthFailure(val message: String? = null) : SpeechFailure()
    data class NetworkFailure(val message: String? = null) : SpeechFailure()
    data class ProviderFailure(val message: String? = null) : SpeechFailure()
    data class Unsupported(val message: String? = null) : SpeechFailure()
    data class InvalidRequest(val message: String? = null) : SpeechFailure()
    data class Unknown(
        val message: String? = null,
        val causeCode: String? = null
    ) : SpeechFailure()
}
