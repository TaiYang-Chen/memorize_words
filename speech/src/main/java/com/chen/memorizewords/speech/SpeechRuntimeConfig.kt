package com.chen.memorizewords.speech

import com.chen.memorizewords.speech.api.SpeechProviderType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class SpeechRuntimeConfig @Inject constructor(
    val wordTtsProvider: SpeechProviderType,
    val sentenceTtsProvider: SpeechProviderType,
    val evaluationProvider: SpeechProviderType,
    val baiduAppId: String,
    val baiduApiKey: String,
    val baiduSecretKey: String,
    val aliyunAppKey: String,
    val aliyunWordVoice: String,
    val aliyunSentenceVoice: String
)
