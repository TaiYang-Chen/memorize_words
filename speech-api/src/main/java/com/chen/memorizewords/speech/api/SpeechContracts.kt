package com.chen.memorizewords.speech.api

enum class SpeechProviderType {
    BAIDU,
    ALIYUN
}

enum class SpeechCapability {
    WORD_TTS,
    SENTENCE_TTS,
    SHADOWING_EVALUATION
}

interface SpeechService {
    suspend fun execute(task: SpeechTask): SpeechResult
    fun getCapabilities(provider: SpeechProviderType? = null): Set<SpeechCapability>
}

interface SpeechProviderSelector {
    fun select(task: SpeechTask): SpeechProviderType
}

interface SpeechProviderAdapter {
    val provider: SpeechProviderType
    val capabilities: Set<SpeechCapability>

    suspend fun execute(task: SpeechTask, traceId: String): SpeechResult
}
