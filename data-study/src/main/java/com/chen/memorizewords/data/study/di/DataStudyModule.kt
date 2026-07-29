package com.chen.memorizewords.data.study.di

import android.content.Context
import com.chen.memorizewords.core.database.DestructiveRoomDatabaseFactory
import com.chen.memorizewords.core.database.NewArchitectureDatabase
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.mmkv.plan.StudyPlanDataSource
import com.chen.memorizewords.data.study.local.mmkv.plan.StudyPlanDataSourceImpl
import com.chen.memorizewords.data.study.repository.bootstrap.DailyStudySnapshotLocalStateStore
import com.chen.memorizewords.data.study.repository.bootstrap.FavoritesSnapshotLocalStateStore
import com.chen.memorizewords.data.study.repository.record.DailyStudyRepositoryImpl
import com.chen.memorizewords.data.study.repository.record.BusinessDateProviderImpl
import com.chen.memorizewords.data.study.repository.record.DailyStudyProjectionStoreImpl
import com.chen.memorizewords.data.study.repository.study.FavoritesRepositoryImpl
import com.chen.memorizewords.data.study.repository.study.StudyPlanRepositoryImpl
import com.chen.memorizewords.domain.study.repository.DailyStudySnapshotPort
import com.chen.memorizewords.domain.study.repository.FavoritesSnapshotPort
import com.chen.memorizewords.domain.study.repository.record.DailyStudyRepository
import com.chen.memorizewords.domain.study.repository.record.BusinessDateProvider
import com.chen.memorizewords.domain.study.repository.record.DailyStudyProjectionStore
import com.chen.memorizewords.domain.study.repository.word.FavoritesRepository
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.tencent.mmkv.MMKV

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStudyModule {
    @Binds
    abstract fun bindStudyPlanRepository(impl: StudyPlanRepositoryImpl): StudyPlanRepository

    @Binds
    abstract fun bindStudyPlanLocalStatePort(
        impl: StudyPlanRepositoryImpl
    ): StudyPlanLocalStatePort

    @Binds
    abstract fun bindDailyStudyRepository(
        impl: DailyStudyRepositoryImpl
    ): DailyStudyRepository

    @Binds
    abstract fun bindBusinessDateProvider(impl: BusinessDateProviderImpl): BusinessDateProvider

    @Binds
    abstract fun bindDailyStudyProjectionStore(
        impl: DailyStudyProjectionStoreImpl
    ): DailyStudyProjectionStore

    @Binds
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    abstract fun bindDailyStudySnapshotPort(
        impl: DailyStudySnapshotLocalStateStore
    ): DailyStudySnapshotPort

    @Binds
    abstract fun bindFavoritesSnapshotPort(
        impl: FavoritesSnapshotLocalStateStore
    ): FavoritesSnapshotPort
}

@Module
@InstallIn(SingletonComponent::class)
object DataStudyDatabaseModule {
    @Provides
    @Singleton
    fun provideStudyDatabase(@ApplicationContext context: Context): StudyDatabase {
        return DestructiveRoomDatabaseFactory(
            databaseName = NewArchitectureDatabase.contextName("study")
        ).build(context, StudyDatabase::class.java)
    }

    @Provides
    fun provideWordFavoritesDao(database: StudyDatabase) = database.wordFavoritesDao()

    @Provides
    fun provideDailyStudyDurationDao(database: StudyDatabase) = database.dailyStudyDurationDao()

    @Provides
    fun provideCheckInRecordDao(database: StudyDatabase) = database.checkInRecordDao()

    @Provides
    fun provideStudyPendingOutboxDao(database: StudyDatabase) = database.studyPendingOutboxDao()

    @Provides
    @Singleton
    fun provideStudyPlanDataSource(mmkv: MMKV): StudyPlanDataSource {
        return StudyPlanDataSourceImpl(mmkv)
    }
}
