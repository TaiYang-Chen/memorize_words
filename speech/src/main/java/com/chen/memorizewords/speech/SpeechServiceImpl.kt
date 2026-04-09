package com.chen.memorizewords.speech

import android.util.Log
import com.chen.memorizewords.speech.api.SentenceAudioResult
import com.chen.memorizewords.speech.api.SpeechAudioOutput
import com.chen.memorizewords.speech.api.SpeechAudioSuccess
import com.chen.memorizewords.speech.api.SpeechCapability
import com.chen.memorizewords.speech.api.SpeechFailure
import com.chen.memorizewords.speech.api.SpeechProviderAdapter
import com.chen.memorizewords.speech.api.SpeechProviderSelector
import com.chen.memorizewords.speech.api.SpeechProviderType
import com.chen.memorizewords.speech.api.SpeechResult
import com.chen.memorizewords.speech.api.SpeechService
import com.chen.memorizewords.speech.api.SpeechTask
import com.chen.memorizewords.speech.api.WordAudioResult
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SpeechServiceImpl @Inject constructor(
    private val aliyunAdapter: AliyunSpeechProviderAdapter,
    private val baiduAdapter: BaiduSpeechProviderAdapter,
    private val providerSelector: SpeechProviderSelector,
    private val speechCacheStore: SpeechCacheStore,
    private val runtimeConfig: SpeechRuntimeConfig
) : SpeechService {

    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<SpeechResult>>()

    override suspend fun execute(task: SpeechTask): SpeechResult {
        val traceId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val provider = providerSelector.select(task)
        val adapter = resolveAdapter(provider)
        if (!adapter.capabilities.contains(task.requiredCapability)) {
            val result = failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.Unsupported(
                    "Provider $provider does not support ${task.requiredCapability}."
                ),
                message = "Speech capability is unsupported by the selected provider."
            )
            logResult(task, result, startedAt, cacheHit = false)
            return result
        }

        return if (task is SpeechTask.SynthesizeWord || task is SpeechTask.SynthesizeSentence) {
            executeSynthesizingTask(task, traceId, provider, adapter, startedAt)
        } else {
            val executed = adapter.execute(task, traceId)
            logResult(task, executed, startedAt, cacheHit = false)
            executed
        }
    }

    override fun getCapabilities(provider: SpeechProviderType?): Set<SpeechCapability> {
        return if (provider != null) {
            resolveAdapter(provider).capabilities
        } else {
            setOf(
                runtimeConfig.wordTtsProvider,
                runtimeConfig.sentenceTtsProvider,
                runtimeConfig.evaluationProvider
            ).flatMapTo(linkedSetOf()) { resolveAdapter(it).capabilities }
        }
    }

    private suspend fun executeSynthesizingTask(
        task: SpeechTask,
        traceId: String,
        provider: SpeechProviderType,
        adapter: SpeechProviderAdapter,
        startedAt: Long
    ): SpeechResult {
        val cacheKey = speechCacheStore.stableHash(task.cacheDescriptor(provider.name))
        speechCacheStore.getCachedFile(cacheKey)?.let { cachedFile ->
            val result = toCachedSuccess(task, provider, traceId, cachedFile, cacheKey)
            logResult(task, result, startedAt, cacheHit = true)
            return result
        }

        return coroutineScope {
            val deferred = registerInFlight(scope = this, cacheKey = cacheKey) {
                val raw = adapter.execute(task, traceId)
                normalizeAudioResult(task, raw, cacheKey)
            }
            val result = try {
                deferred.await()
            } finally {
                clearInFlight(cacheKey, deferred)
            }
            logResult(task, result, startedAt, cacheHit = result is SpeechAudioSuccess && result.isFromCache)
            result
        }
    }

    private suspend fun registerInFlight(
        scope: CoroutineScope,
        cacheKey: String,
        block: suspend () -> SpeechResult
    ): Deferred<SpeechResult> {
        return inFlightMutex.withLock {
            inFlight[cacheKey] ?: scope.async { block() }.also { inFlight[cacheKey] = it }
        }
    }

    private suspend fun clearInFlight(cacheKey: String, deferred: Deferred<SpeechResult>) {
        inFlightMutex.withLock {
            if (inFlight[cacheKey] === deferred) {
                inFlight.remove(cacheKey)
            }
        }
    }

    private fun normalizeAudioResult(task: SpeechTask, result: SpeechResult, cacheKey: String): SpeechResult {
        val audioSuccess = result as? SpeechAudioSuccess ?: return result
        val cacheFile = when (val output = audioSuccess.audioOutput) {
            is SpeechAudioOutput.FileOutput -> speechCacheStore.copyIntoCache(cacheKey, File(output.filePath))
            is SpeechAudioOutput.UrlOutput -> null
            is SpeechAudioOutput.StreamOutput -> null
        }
        val normalizedOutput = cacheFile?.let {
            SpeechAudioOutput.FileOutput(
                filePath = it.absolutePath,
                format = audioSuccess.audioOutput.format
            )
        } ?: audioSuccess.audioOutput
        return when (task) {
            is SpeechTask.SynthesizeWord -> WordAudioResult(
                provider = audioSuccess.provider,
                traceId = audioSuccess.traceId,
                audioOutput = normalizedOutput,
                cacheKey = cacheKey,
                isFromCache = cacheFile != null
            )

            is SpeechTask.SynthesizeSentence -> SentenceAudioResult(
                provider = audioSuccess.provider,
                traceId = audioSuccess.traceId,
                audioOutput = normalizedOutput,
                cacheKey = cacheKey,
                isFromCache = cacheFile != null
            )

            else -> result
        }
    }

    private fun toCachedSuccess(
        task: SpeechTask,
        provider: SpeechProviderType,
        traceId: String,
        cachedFile: File,
        cacheKey: String
    ): SpeechResult {
        val output = SpeechAudioOutput.FileOutput(cachedFile.absolutePath)
        return when (task) {
            is SpeechTask.SynthesizeWord -> WordAudioResult(
                provider = provider,
                traceId = traceId,
                audioOutput = output,
                cacheKey = cacheKey,
                isFromCache = true
            )

            is SpeechTask.SynthesizeSentence -> SentenceAudioResult(
                provider = provider,
                traceId = traceId,
                audioOutput = output,
                cacheKey = cacheKey,
                isFromCache = true
            )

            else -> error("Unexpected cached task type")
        }
    }

    private fun resolveAdapter(provider: SpeechProviderType): SpeechProviderAdapter {
        return when (provider) {
            SpeechProviderType.ALIYUN -> aliyunAdapter
            SpeechProviderType.BAIDU -> baiduAdapter
        }
    }

    private fun logResult(
        task: SpeechTask,
        result: SpeechResult,
        startedAt: Long,
        cacheHit: Boolean
    ) {
        val durationMs = System.currentTimeMillis() - startedAt
        val failureType = (result as? com.chen.memorizewords.speech.api.SpeechFailureResult)
            ?.failure
            ?.javaClass
            ?.simpleName
            ?: "NONE"
        Log.i(
            "SpeechService",
            "traceId=${result.traceId}, provider=${result.provider}, taskType=${task.javaClass.simpleName}, " +
                "requiredCapability=${task.requiredCapability}, cacheHit=$cacheHit, durationMs=$durationMs, failureType=$failureType"
        )
    }
}
