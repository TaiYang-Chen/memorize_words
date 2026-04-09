package com.chen.memorizewords.speech

import com.chen.memorizewords.speech.api.ShadowingEvaluationResult
import com.chen.memorizewords.speech.api.SentenceAudioResult
import com.chen.memorizewords.speech.api.SpeechAudioInput
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
class BaiduSpeechProviderAdapter @Inject constructor(
    private val speechCacheStore: SpeechCacheStore,
    private val ttsClient: BaiduTtsClient,
    private val shadowingClient: BaiduShadowingClient,
    private val shadowingScorer: BaiduShadowingScorer
) : SpeechProviderAdapter {

    override val provider: SpeechProviderType = SpeechProviderType.BAIDU
    override val capabilities: Set<SpeechCapability> = setOf(
        SpeechCapability.WORD_TTS,
        SpeechCapability.SENTENCE_TTS,
        SpeechCapability.SHADOWING_EVALUATION
    )

    override suspend fun execute(task: SpeechTask, traceId: String): SpeechResult {
        return when (task) {
            is SpeechTask.SynthesizeSentence -> synthesize(
                text = task.text,
                locale = task.locale,
                voice = task.voice,
                audioFormat = task.audioFormat,
                traceId = traceId,
                task = task
            )

            is SpeechTask.SynthesizeWord -> synthesize(
                text = task.text,
                locale = task.locale,
                voice = task.voice,
                audioFormat = task.audioFormat,
                traceId = traceId,
                task = task
            )

            is SpeechTask.EvaluateShadowing -> evaluate(task, traceId)
        }
    }

    private suspend fun synthesize(
        text: String,
        locale: String,
        voice: String,
        audioFormat: com.chen.memorizewords.speech.api.SpeechAudioFormat,
        traceId: String,
        task: SpeechTask
    ): SpeechResult {
        return runCatching {
            val audioFile = ttsClient.synthesize(
                task = SpeechSynthesisTask(
                    text = text,
                    locale = locale,
                    voice = voice,
                    audioFormat = audioFormat
                ),
                traceId = traceId
            )
            val output = SpeechAudioOutput.FileOutput(
                filePath = audioFile.absolutePath,
                format = audioFormat
            )
            val cacheKey = speechCacheStore.stableHash(task.cacheDescriptor(provider.name))
            when (task) {
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

                else -> error("Unexpected task type")
            }
        }.getOrElse { error ->
            mapError(traceId, error)
        }
    }

    private suspend fun evaluate(task: SpeechTask.EvaluateShadowing, traceId: String): SpeechResult {
        val normalizedInput = when (val input = task.audioInput) {
            is SpeechAudioInput.FileInput -> input
            is SpeechAudioInput.ByteArrayInput -> {
                val tempFile = speechCacheStore.createTempFile("shadowing_$traceId")
                tempFile.writeBytes(input.bytes)
                SpeechAudioInput.FileInput(
                    filePath = tempFile.absolutePath,
                    format = input.format
                )
            }

            is SpeechAudioInput.StreamInput -> {
                return failureResult(
                    provider = provider,
                    traceId = traceId,
                    failure = SpeechFailure.Unsupported("Stream input is not implemented."),
                    message = "Stream audio input is not supported yet."
                )
            }
        }
        return runCatching {
            val recognized = shadowingClient.recognize(
                audioInput = normalizedInput,
                locale = task.locale
            )
            val scores = shadowingScorer.score(
                referenceText = task.referenceText,
                recognizedText = recognized.recognizedText
            )
            ShadowingEvaluationResult(
                provider = provider,
                traceId = traceId,
                totalScore = scores.totalScore,
                pronunciationScore = scores.pronunciationScore,
                fluencyScore = scores.fluencyScore,
                recognizedText = recognized.recognizedText
            )
        }.getOrElse { error ->
            mapError(traceId, error)
        }
    }

    private fun mapError(traceId: String, throwable: Throwable): SpeechResult {
        return when (val error = throwable.toBaiduClientException()) {
            is BaiduAuthException -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.AuthFailure(error.message),
                message = error.message,
                causeCode = error.code
            )

            is BaiduNetworkException -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.NetworkFailure(error.message),
                message = error.message
            )

            is BaiduApiException -> failureResult(
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
