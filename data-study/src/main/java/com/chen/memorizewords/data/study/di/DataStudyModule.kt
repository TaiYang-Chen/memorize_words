package com.chen.memorizewords.data.study.di

import android.content.Context
import com.chen.memorizewords.core.database.DestructiveRoomDatabaseFactory
import com.chen.memorizewords.core.database.NewArchitectureDatabase
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.mmkv.plan.StudyPlanDataSource
import com.chen.memorizewords.data.study.local.mmkv.plan.StudyPlanDataSourceImpl
import com.chen.memorizewords.data.study.repository.bootstrap.StudySnapshotLocalStateStore
import com.chen.memorizewords.data.study.repository.WordLearningRepositoryImpl
import com.chen.memorizewords.data.study.repository.record.LearningRecordRepositoryImpl
import com.chen.memorizewords.data.study.repository.study.FavoritesRepositoryImpl
import com.chen.memorizewords.data.study.repository.study.StudyPlanRepositoryImpl
import com.chen.memorizewords.domain.study.repository.StudySnapshotLocalStatePort
import com.chen.memorizewords.domain.study.repository.WordLearningRepository
import com.chen.memorizewords.domain.study.repository.WordLearningStateStore
import com.chen.memorizewords.domain.study.repository.record.LearningRecordRepository
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
    abstract fun bindWordLearningRepository(impl: WordLearningRepositoryImpl): WordLearningRepository

    @Binds
    abstract fun bindWordLearningStateStore(impl: WordLearningRepositoryImpl): WordLearningStateStore

    @Binds
    abstract fun bindStudyPlanRepository(impl: StudyPlanRepositoryImpl): StudyPlanRepository

    @Binds
    abstract fun bindStudyPlanLocalStatePort(
        impl: StudyPlanRepositoryImpl
    ): StudyPlanLocalStatePort

    @Binds
    abstract fun bindLearningRecordRepository(
        impl: LearningRecordRepositoryImpl
    ): LearningRecordRepository

    @Binds
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    abstract fun bindStudySnapshotLocalStatePort(
        impl: StudySnapshotLocalStateStore
    ): StudySnapshotLocalStatePort
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
    fun provideWordLearningStateDao(database: StudyDatabase) = database.wordLearningStateDao()

    @Provides
    fun provideWordFavoritesDao(database: StudyDatabase) = database.wordFavoritesDao()

    @Provides
    fun provideWordStudyRecordsDao(database: StudyDatabase) = database.wordStudyRecordsDao()

    @Provides
    fun provideDailyStudyDurationDao(database: StudyDatabase) = database.dailyStudyDurationDao()

    @Provides
    fun provideCheckInRecordDao(database: StudyDatabase) = database.checkInRecordDao()

    @Provides
    fun provideWordBookProgressDao(database: StudyDatabase) = database.wordBookProgressDao()

    @Provides
    @Singleton
    fun provideStudyPlanDataSource(mmkv: MMKV): StudyPlanDataSource {
        return StudyPlanDataSourceImpl(mmkv)
    }
}
