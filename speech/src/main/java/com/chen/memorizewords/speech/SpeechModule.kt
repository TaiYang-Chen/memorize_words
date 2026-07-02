package com.chen.memorizewords.speech

import com.chen.memorizewords.domain.practice.speech.PracticeSpeechSynthesizer
import com.chen.memorizewords.domain.practice.speech.ShadowingEvaluator
import com.chen.memorizewords.speech.api.SpeechProviderSelector
import com.chen.memorizewords.speech.api.SpeechProviderType
import com.chen.memorizewords.speech.api.SpeechService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Qualifier
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaiduHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechBindingModule {

    @Binds
    abstract fun bindSpeechService(impl: SpeechServiceImpl): SpeechService

    @Binds
    abstract fun bindPracticeSpeechSynthesizer(
        impl: PracticeSpeechDomainAdapter
    ): PracticeSpeechSynthesizer

    @Binds
    abstract fun bindShadowingEvaluator(
        impl: PracticeSpeechDomainAdapter
    ): ShadowingEvaluator

    @Binds
    abstract fun bindSpeechProviderSelector(
        impl: DefaultSpeechProviderSelector
    ): SpeechProviderSelector

}

@Module
@InstallIn(SingletonComponent::class)
object SpeechConfigModule {

    @Provides
    @Singleton
    @BaiduHttpClient
    fun provideBaiduOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideSpeechRuntimeConfig(): SpeechRuntimeConfig {
        return SpeechRuntimeConfig(
            wordTtsProvider = parseProvider(BuildConfig.WORD_TTS_PROVIDER, SpeechProviderType.ALIYUN),
            sentenceTtsProvider = parseProvider(BuildConfig.SENTENCE_TTS_PROVIDER, SpeechProviderType.BAIDU),
            evaluationProvider = parseProvider(BuildConfig.EVALUATION_PROVIDER, SpeechProviderType.XUNFEI)
        )
    }

    private fun parseProvider(
        value: String,
        fallback: SpeechProviderType
    ): SpeechProviderType {
        return runCatching { SpeechProviderType.valueOf(value) }
            .getOrDefault(fallback)
    }
}
