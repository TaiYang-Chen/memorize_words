package com.chen.memorizewords.data.practice.di

import android.content.Context
import com.chen.memorizewords.core.database.DestructiveRoomDatabaseFactory
import com.chen.memorizewords.core.database.NewArchitectureDatabase
import com.chen.memorizewords.data.practice.PracticeReportRepositoryImpl
import com.chen.memorizewords.data.practice.repository.ExamPracticeRepositoryImpl
import com.chen.memorizewords.data.practice.repository.ListeningPracticePreferencesRepositoryImpl
import com.chen.memorizewords.data.practice.repository.ListeningPracticePreferencesStore
import com.chen.memorizewords.data.practice.repository.MmkvListeningPracticePreferencesStore
import com.chen.memorizewords.data.practice.repository.bootstrap.PracticeSnapshotLocalStateStore
import com.chen.memorizewords.data.practice.repository.PracticeRecordRepositoryImpl
import com.chen.memorizewords.data.practice.repository.PracticeSessionRecordRepositoryImpl
import com.chen.memorizewords.data.practice.repository.PracticeSettingsRepositoryImpl
import com.chen.memorizewords.data.practice.local.PracticeDatabase
import com.chen.memorizewords.data.practice.remote.practice.RemoteExamPracticeDataSource
import com.chen.memorizewords.data.practice.remote.practice.RemoteExamPracticeDataSourceImpl
import com.chen.memorizewords.data.practice.remoteapi.api.practice.ExamPracticeApiService
import com.chen.memorizewords.domain.practice.PracticeReportRepository
import com.chen.memorizewords.domain.practice.PracticeRecordRepository
import com.chen.memorizewords.domain.practice.PracticeSnapshotLocalStatePort
import com.chen.memorizewords.domain.practice.PracticeSessionRecordRepository
import com.chen.memorizewords.domain.practice.PracticeSettingsLocalStatePort
import com.chen.memorizewords.domain.practice.PracticeSettingsRepository
import com.chen.memorizewords.domain.practice.repository.ExamPracticeRepository
import com.chen.memorizewords.domain.practice.repository.ListeningPracticePreferencesRepository as DomainListeningPracticePreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class DataPracticeBindingModule {
    @Binds
    abstract fun bindPracticeReportRepository(
        impl: PracticeReportRepositoryImpl
    ): PracticeReportRepository

    @Binds
    abstract fun bindPracticeSettingsRepository(
        impl: PracticeSettingsRepositoryImpl
    ): PracticeSettingsRepository

    @Binds
    abstract fun bindPracticeSettingsLocalStatePort(
        impl: PracticeSettingsRepositoryImpl
    ): PracticeSettingsLocalStatePort

    @Binds
    abstract fun bindPracticeRecordRepository(
        impl: PracticeRecordRepositoryImpl
    ): PracticeRecordRepository

    @Binds
    abstract fun bindPracticeSessionRecordRepository(
        impl: PracticeSessionRecordRepositoryImpl
    ): PracticeSessionRecordRepository

    @Binds
    abstract fun bindPracticeSnapshotLocalStatePort(
        impl: PracticeSnapshotLocalStateStore
    ): PracticeSnapshotLocalStatePort

    @Binds
    abstract fun bindListeningPracticePreferencesStore(
        impl: MmkvListeningPracticePreferencesStore
    ): ListeningPracticePreferencesStore

    @Binds
    abstract fun bindListeningPracticePreferencesRepository(
        impl: ListeningPracticePreferencesRepositoryImpl
    ): DomainListeningPracticePreferencesRepository

    @Binds
    abstract fun bindExamPracticeRepository(
        impl: ExamPracticeRepositoryImpl
    ): ExamPracticeRepository

    @Binds
    abstract fun bindRemoteExamPracticeDataSource(
        impl: RemoteExamPracticeDataSourceImpl
    ): RemoteExamPracticeDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object DataPracticeDatabaseModule {
    @Provides
    @Singleton
    fun providePracticeDatabase(@ApplicationContext context: Context): PracticeDatabase {
        return DestructiveRoomDatabaseFactory(
            databaseName = NewArchitectureDatabase.contextName("practice")
        ).build(context, PracticeDatabase::class.java)
    }

    @Provides
    fun providePracticeReportDao(database: PracticeDatabase) = database.practiceReportDao()

    @Provides
    fun provideDailyPracticeDurationDao(database: PracticeDatabase) =
        database.dailyPracticeDurationDao()

    @Provides
    fun provideExamPracticeDao(database: PracticeDatabase) = database.examPracticeDao()

    @Provides
    fun providePracticeSessionRecordDao(database: PracticeDatabase) =
        database.practiceSessionRecordDao()

    @Provides
    @Singleton
    fun provideExamPracticeApiService(retrofit: Retrofit): ExamPracticeApiService {
        return retrofit.create(ExamPracticeApiService::class.java)
    }
}
