package com.chen.memorizewords.speech

import com.chen.memorizewords.speech.api.SentenceAudioResult
import com.chen.memorizewords.speech.api.SpeechAudioOutput
import com.chen.memorizewords.speech.api.SpeechCapability
import com.chen.memorizewords.speech.api.SpeechFailure
import com.chen.memorizewords.speech.api.SpeechProviderAdapter
import com.chen.memorizewords.speech.api.SpeechProviderType
import com.chen.memorizewords.speech.api.SpeechResult
import com.chen.memorizewords.speech.api.SpeechTask
import com.chen.memorizewords.speech.api.WordAudioResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AliyunSpeechProviderAdapter @Inject constructor(
    private val speechCacheStore: SpeechCacheStore,
    private val ttsClient: AliyunTtsClient
) : SpeechProviderAdapter {

    override val provider: SpeechProviderType = SpeechProviderType.ALIYUN
    override val capabilities: Set<SpeechCapability> = setOf(
        SpeechCapability.WORD_TTS,
        SpeechCapability.SENTENCE_TTS
    )

    override suspend fun execute(task: SpeechTask, traceId: String): SpeechResult {
        return when (task) {
            is SpeechTask.SynthesizeSentence -> synthesize(
                task = SpeechSynthesisTask(
                    text = task.text,
                    locale = task.locale,
                    voice = task.voice,
                    audioFormat = task.audioFormat
                ),
                traceId = traceId,
                originalTask = task
            )

            is SpeechTask.SynthesizeWord -> synthesize(
                task = SpeechSynthesisTask(
                    text = task.text,
                    locale = task.locale,
                    voice = task.voice,
                    audioFormat = task.audioFormat
                ),
                traceId = traceId,
                originalTask = task
            )

            is SpeechTask.EvaluateShadowing -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.Unsupported("Aliyun provider does not support evaluation yet."),
                message = "Aliyun speech evaluation is not implemented."
            )
        }
    }

    private suspend fun synthesize(
        task: SpeechSynthesisTask,
        traceId: String,
        originalTask: SpeechTask
    ): SpeechResult {
        return runCatching {
            val audioFile = ttsClient.synthesize(task, traceId)
            val cacheKey = speechCacheStore.stableHash(originalTask.cacheDescriptor(provider.name))
            val output = SpeechAudioOutput.FileOutput(
                filePath = audioFile.absolutePath,
                format = task.audioFormat
            )
            when (originalTask) {
                is SpeechTask.SynthesizeWord -> WordAudioResult(
                    provider = provider,
                    traceId = traceId,
                    audioOutput = output,
                    cacheKey = cacheKey,
                    isFromCache = false
                )

                is SpeechTask.SynthesizeSentence -> SentenceAudioResult(
                    provider = provider,
                    traceId = traceId,
                    audioOutput = output,
                    cacheKey = cacheKey,
                    isFromCache = false
                )

                else -> error("Unexpected synthesis task")
            }
        }.getOrElse { error ->
            mapError(traceId, error)
        }
    }

    private fun mapError(traceId: String, throwable: Throwable): SpeechResult {
        return when (val error = throwable.toAliyunClientException()) {
            is AliyunAuthException -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.AuthFailure(error.message),
                message = error.message,
                causeCode = error.code
            )

            is AliyunNetworkException -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.NetworkFailure(error.message),
                message = error.message
            )

            is AliyunApiException -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.ProviderFailure(error.message),
                message = error.message,
                causeCode = error.code
            )

            else -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.Unknown(error.message),
                message = error.message
            )
        }
    }
}
