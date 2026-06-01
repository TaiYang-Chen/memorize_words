package com.chen.memorizewords.feature.learning.ui.practice.listening.audio

import com.chen.memorizewords.domain.practice.usecase.SynthesizeSpeechUseCase
import com.chen.memorizewords.domain.practice.speech.DefaultSpeechFailureResult
import com.chen.memorizewords.domain.practice.speech.PracticeSpeechSynthesizer
import com.chen.memorizewords.domain.practice.speech.SentenceAudioResult
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput
import com.chen.memorizewords.domain.practice.speech.SpeechFailure
import com.chen.memorizewords.domain.practice.speech.SpeechProviderType
import com.chen.memorizewords.domain.practice.speech.SpeechResult
import com.chen.memorizewords.domain.practice.speech.SpeechTask
import com.chen.memorizewords.domain.practice.speech.WordAudioResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningSpeechControllerTest {

    @Test
    fun `resolveWordSpeech caches by word id and locale`() = runTest {
        val service = FakeSpeechService()
        val controller = ListeningSpeechController(SynthesizeSpeechUseCase(service))
        val key = ListeningWordSpeechKey(wordId = 1L, locale = "en-US")

        val first = controller.resolveWordSpeech(key, text = "alpha")
        val second = controller.resolveWordSpeech(key, text = "alpha")

        assertSame(first, second)
        assertEquals(1, service.tasks.size)
        assertEquals(SpeechTask.SynthesizeWord("alpha", "en-US"), service.tasks.single())
    }

    @Test
    fun `resolveSentenceSpeech caches by word text and locale`() = runTest {
        val service = FakeSpeechService()
        val controller = ListeningSpeechController(SynthesizeSpeechUseCase(service))
        val key = ListeningSentenceSpeechKey(
            wordId = 1L,
            text = "Alpha is first.",
            locale = "en-GB"
        )

        val first = controller.resolveSentenceSpeech(key)
        val second = controller.resolveSentenceSpeech(key)

        assertSame(first, second)
        assertEquals(1, service.tasks.size)
        assertEquals(
            SpeechTask.SynthesizeSentence("Alpha is first.", "en-GB"),
            service.tasks.single()
        )
    }

    @Test
    fun `failed speech requests cache null result to avoid duplicate synthesis`() = runTest {
        val service = FakeSpeechService(resultFactory = { task ->
            DefaultSpeechFailureResult(
                provider = SpeechProviderType.BAIDU,
                traceId = "trace-${task.hashCode()}",
                failure = SpeechFailure.ProviderFailure("no audio")
            )
        })
        val controller = ListeningSpeechController(SynthesizeSpeechUseCase(service))
        val key = ListeningWordSpeechKey(wordId = 2L, locale = "en-US")

        assertNull(controller.resolveWordSpeech(key, text = "beta"))
        assertNull(controller.resolveWordSpeech(key, text = "beta"))

        assertEquals(1, service.tasks.size)
    }

    @Test
    fun `clear drops cached audio`() = runTest {
        val service = FakeSpeechService()
        val controller = ListeningSpeechController(SynthesizeSpeechUseCase(service))
        val key = ListeningWordSpeechKey(wordId = 3L, locale = "en-US")

        assertTrue(controller.resolveWordSpeech(key, text = "gamma") != null)
        controller.clear()
        assertTrue(controller.resolveWordSpeech(key, text = "gamma") != null)

        assertEquals(2, service.tasks.size)
    }

    private class FakeSpeechService(
        private val resultFactory: (SpeechTask) -> SpeechResult = { task ->
            when (task) {
                is SpeechTask.SynthesizeWord -> WordAudioResult(
                    provider = SpeechProviderType.BAIDU,
                    traceId = "word-${task.text}-${task.locale}",
                    audioOutput = SpeechAudioOutput.FileOutput("/tmp/${task.text}.mp3"),
                    cacheKey = "word:${task.text}:${task.locale}",
                    isFromCache = false
                )

                is SpeechTask.SynthesizeSentence -> SentenceAudioResult(
                    provider = SpeechProviderType.BAIDU,
                    traceId = "sentence-${task.text}-${task.locale}",
                    audioOutput = SpeechAudioOutput.FileOutput("/tmp/${task.text.hashCode()}.mp3"),
                    cacheKey = "sentence:${task.text}:${task.locale}",
                    isFromCache = false
                )

                is SpeechTask.EvaluateShadowing -> error("Unexpected shadowing task")
            }
        }
    ) : PracticeSpeechSynthesizer {
        val tasks = mutableListOf<SpeechTask>()

        override suspend fun synthesize(task: SpeechTask): SpeechResult {
            tasks += task
            return resultFactory(task)
        }
    }
}
