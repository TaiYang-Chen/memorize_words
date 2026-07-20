package com.chen.memorizewords.data.floating.di

import android.content.Context
import com.chen.memorizewords.core.database.DestructiveRoomDatabaseFactory
import com.chen.memorizewords.core.database.NewArchitectureDatabase
import com.chen.memorizewords.data.floating.repository.bootstrap.FloatingSnapshotLocalStateStore
import com.chen.memorizewords.data.floating.local.FloatingDatabase
import com.chen.memorizewords.data.floating.local.FloatingActivationStateStore
import com.chen.memorizewords.data.floating.repository.FloatingWordDisplayRecordRepositoryImpl
import com.chen.memorizewords.data.floating.repository.FloatingWordSettingsRepositoryImpl
import com.chen.memorizewords.data.floating.repository.CharacterPackRepositoryImpl
import com.chen.memorizewords.data.floating.repository.DownloadedSpritePackSource
import com.chen.memorizewords.data.floating.remoteapi.CharacterPackApiService
import com.chen.memorizewords.core.sprite.DownloadedSpriteSource
import com.chen.memorizewords.core.sprite.SpritePackSource
import com.chen.memorizewords.domain.floating.FloatingSnapshotLocalStatePort
import com.chen.memorizewords.domain.floating.FloatingSettingsLocalStatePort
import com.chen.memorizewords.domain.floating.repository.FloatingWordDisplayRecordRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import com.chen.memorizewords.domain.floating.repository.FloatingActivationStateRepository
import com.chen.memorizewords.domain.floating.service.FloatingActivationEventReporter
import com.chen.memorizewords.data.floating.repository.LogcatFloatingActivationEventReporter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CharacterPackHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class DataFloatingModule {
    @Binds
    abstract fun bindFloatingWordSettingsRepository(
        impl: FloatingWordSettingsRepositoryImpl
    ): FloatingWordSettingsRepository

    @Binds
    abstract fun bindFloatingSettingsLocalStatePort(
        impl: FloatingWordSettingsRepositoryImpl
    ): FloatingSettingsLocalStatePort

    @Binds
    abstract fun bindFloatingWordDisplayRecordRepository(
        impl: FloatingWordDisplayRecordRepositoryImpl
    ): FloatingWordDisplayRecordRepository

    @Binds
    abstract fun bindFloatingSnapshotLocalStatePort(
        impl: FloatingSnapshotLocalStateStore
    ): FloatingSnapshotLocalStatePort

    @Binds
    abstract fun bindCharacterPackRepository(
        impl: CharacterPackRepositoryImpl
    ): CharacterPackRepository

    @Binds
    abstract fun bindFloatingActivationStateRepository(
        impl: FloatingActivationStateStore
    ): FloatingActivationStateRepository

    @Binds
    abstract fun bindFloatingActivationEventReporter(
        impl: LogcatFloatingActivationEventReporter
    ): FloatingActivationEventReporter
}

@Module
@InstallIn(SingletonComponent::class)
object DataFloatingDatabaseModule {
    @Provides
    @Singleton
    fun provideFloatingDatabase(@ApplicationContext context: Context): FloatingDatabase {
        return DestructiveRoomDatabaseFactory(
            databaseName = NewArchitectureDatabase.contextName("floating")
        ).build(context, FloatingDatabase::class.java)
    }

    @Provides
    fun provideFloatingWordDisplayRecordDao(database: FloatingDatabase) =
        database.floatingWordDisplayRecordDao()

    @Provides
    @Singleton
    fun provideCharacterPackApiService(retrofit: Retrofit): CharacterPackApiService =
        retrofit.create(CharacterPackApiService::class.java)

    @Provides
    @Singleton
    @CharacterPackHttpClient
    fun provideCharacterPackHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(false)
            .build()
    }

    @Provides
    @Singleton
    @DownloadedSpriteSource
    fun provideDownloadedSpritePackSource(source: DownloadedSpritePackSource): SpritePackSource = source
}
