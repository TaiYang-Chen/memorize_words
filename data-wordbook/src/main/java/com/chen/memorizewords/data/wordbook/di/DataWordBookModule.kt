package com.chen.memorizewords.data.wordbook.di

import android.content.Context
import com.chen.memorizewords.core.database.DestructiveRoomDatabaseFactory
import com.chen.memorizewords.core.database.NewArchitectureDatabase
import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import com.chen.memorizewords.data.wordbook.local.mmkv.onboarding.OnboardingSnapshotDataSource
import com.chen.memorizewords.data.wordbook.local.mmkv.onboarding.OnboardingSnapshotDataSourceImpl
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSourceImpl
import com.chen.memorizewords.data.wordbook.remote.wordbook.RemoteWordBookDataSource
import com.chen.memorizewords.data.wordbook.remote.wordbook.RemoteWordBookDataSourceImpl
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.UserDataSyncApiService
import com.chen.memorizewords.data.wordbook.remoteapi.api.wordbook.WordBookApiService
import com.chen.memorizewords.data.wordbook.repository.LearningProgressRepositoryImpl
import com.chen.memorizewords.data.wordbook.repository.MyWordBookRemoteRemover
import com.chen.memorizewords.data.wordbook.repository.RemoteWordBookRepositoryImpl
import com.chen.memorizewords.data.wordbook.repository.RemoteUserSyncMyWordBookRemoteRemover
import com.chen.memorizewords.data.wordbook.repository.RoomWordBookTransactionRunner
import com.chen.memorizewords.data.wordbook.repository.WordBookTransactionRunner
import com.chen.memorizewords.data.wordbook.repository.WordBookWorkCanceller
import com.chen.memorizewords.data.wordbook.repository.WordBookRepositoryImpl
import com.chen.memorizewords.data.wordbook.repository.WordRepositoryImpl
import com.chen.memorizewords.data.wordbook.repository.WorkManagerWordBookWorkCanceller
import com.chen.memorizewords.data.wordbook.repository.bootstrap.WordBookSnapshotLocalStateStore
import com.chen.memorizewords.data.wordbook.repository.learning.LearningCommandRepository
import com.chen.memorizewords.data.wordbook.repository.learning.LearningSyncStateRepository
import com.chen.memorizewords.data.wordbook.repository.learning.BookLearningWriteCoordinatorImpl
import com.chen.memorizewords.data.wordbook.repository.learning.WordBookProgressResetRepositoryImpl
import com.chen.memorizewords.data.wordbook.repository.learning.WordLearningStateRepositoryImpl
import com.chen.memorizewords.data.wordbook.repository.WordBookUpdateRepositoryImpl
import com.chen.memorizewords.data.wordbook.repository.onboarding.OnboardingRepositoryImpl
import com.chen.memorizewords.data.wordbook.repository.wordbook.HttpWordBookContentPackageImporter
import com.chen.memorizewords.data.wordbook.repository.wordbook.WordBookContentPackageImporter
import com.chen.memorizewords.data.wordbook.repository.wordbook.WordBookContentReadinessAdapter
import com.chen.memorizewords.data.wordbook.repository.wordbook.update.WordBookUpdateCoordinatorImpl
import com.chen.memorizewords.domain.wordbook.repository.CurrentWordBookLocalStatePort
import com.chen.memorizewords.domain.study.repository.WordLearningRepository
import com.chen.memorizewords.domain.study.repository.WordLearningStateStore
import com.chen.memorizewords.domain.study.repository.learning.LearningCommandPort
import com.chen.memorizewords.domain.study.repository.learning.LearningSyncStatePort
import com.chen.memorizewords.domain.study.repository.learning.BookLearningWriteCoordinator
import com.chen.memorizewords.domain.wordbook.repository.LearningProgressRepository
import com.chen.memorizewords.domain.wordbook.repository.WordBookContentReadinessPort
import com.chen.memorizewords.domain.wordbook.repository.WordBookSnapshotLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import com.chen.memorizewords.domain.wordbook.repository.WordBookProgressResetRepository
import com.chen.memorizewords.domain.wordbook.repository.WordBookUpdateRepository
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import com.chen.memorizewords.domain.wordbook.repository.shop.RemoteWordBookRepository
import com.chen.memorizewords.domain.wordbook.service.WordBookUpdateCoordinator
import com.chen.memorizewords.domain.word.repository.WordRepository
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
abstract class DataWordBookModule {
    @Binds
    abstract fun bindWordRepository(impl: WordRepositoryImpl): WordRepository

    @Binds
    abstract fun bindWordBookRepository(impl: WordBookRepositoryImpl): WordBookRepository

    @Binds
    abstract fun bindWordBookTransactionRunner(
        impl: RoomWordBookTransactionRunner
    ): WordBookTransactionRunner

    @Binds
    abstract fun bindWordBookWorkCanceller(
        impl: WorkManagerWordBookWorkCanceller
    ): WordBookWorkCanceller

    @Binds
    abstract fun bindMyWordBookRemoteRemover(
        impl: RemoteUserSyncMyWordBookRemoteRemover
    ): MyWordBookRemoteRemover

    @Binds
    abstract fun bindCurrentWordBookLocalStatePort(
        impl: WordBookRepositoryImpl
    ): CurrentWordBookLocalStatePort

    @Binds
    abstract fun bindRemoteWordBookRepository(
        impl: RemoteWordBookRepositoryImpl
    ): RemoteWordBookRepository

    @Binds
    abstract fun bindLearningProgressRepository(
        impl: LearningProgressRepositoryImpl
    ): LearningProgressRepository

    @Binds
    abstract fun bindLearningCommandPort(
        impl: LearningCommandRepository
    ): LearningCommandPort

    @Binds
    abstract fun bindLearningSyncStatePort(
        impl: LearningSyncStateRepository
    ): LearningSyncStatePort

    @Binds
    abstract fun bindBookLearningWriteCoordinator(
        impl: BookLearningWriteCoordinatorImpl
    ): BookLearningWriteCoordinator

    @Binds
    abstract fun bindWordBookProgressResetRepository(
        impl: WordBookProgressResetRepositoryImpl
    ): WordBookProgressResetRepository

    @Binds
    abstract fun bindWordLearningRepository(
        impl: WordLearningStateRepositoryImpl
    ): WordLearningRepository

    @Binds
    abstract fun bindWordLearningStateStore(
        impl: WordLearningStateRepositoryImpl
    ): WordLearningStateStore

    @Binds
    abstract fun bindWordBookSnapshotLocalStatePort(
        impl: WordBookSnapshotLocalStateStore
    ): WordBookSnapshotLocalStatePort

    @Binds
    abstract fun bindWordBookContentReadinessPort(
        impl: WordBookContentReadinessAdapter
    ): WordBookContentReadinessPort

    @Binds
    abstract fun bindWordBookContentPackageImporter(
        impl: HttpWordBookContentPackageImporter
    ): WordBookContentPackageImporter

    @Binds
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository

    @Binds
    abstract fun bindWordBookUpdateRepository(
        impl: WordBookUpdateRepositoryImpl
    ): WordBookUpdateRepository

    @Binds
    abstract fun bindWordBookUpdateCoordinator(
        impl: WordBookUpdateCoordinatorImpl
    ): WordBookUpdateCoordinator

    @Binds
    abstract fun bindRemoteWordBookDataSource(
        impl: RemoteWordBookDataSourceImpl
    ): RemoteWordBookDataSource

    @Binds
    abstract fun bindRemoteUserSyncDataSource(
        impl: RemoteUserSyncDataSourceImpl
    ): RemoteUserSyncDataSource

    @Binds
    abstract fun bindOnboardingSnapshotDataSource(
        impl: OnboardingSnapshotDataSourceImpl
    ): OnboardingSnapshotDataSource

}

@Module
@InstallIn(SingletonComponent::class)
object DataWordBookDatabaseModule {
    @Provides
    @Singleton
    fun provideWordBookDatabase(@ApplicationContext context: Context): WordBookDatabase {
        return DestructiveRoomDatabaseFactory(
            databaseName = NewArchitectureDatabase.contextName("wordbook")
        ).build(context, WordBookDatabase::class.java)
    }

    @Provides
    fun provideWordBookDao(database: WordBookDatabase) = database.wordBookDao()

    @Provides
    fun provideCurrentWordBookSelectionDao(database: WordBookDatabase) =
        database.currentWordBookSelectionDao()

    @Provides
    fun provideWordBookContentStateDao(database: WordBookDatabase) =
        database.wordBookContentStateDao()

    @Provides
    fun provideWordBookSyncStateDao(database: WordBookDatabase) = database.wordBookSyncStateDao()

    @Provides
    fun provideBookWordItemDao(database: WordBookDatabase) = database.wordBookItemDao()

    @Provides
    fun provideWordBookProgressDao(database: WordBookDatabase) = database.wordBookProgressDao()

    @Provides
    fun provideWordLearningStateDao(database: WordBookDatabase) = database.wordLearningStateDao()

    @Provides
    fun provideLearningEventDao(database: WordBookDatabase) = database.learningEventDao()

    @Provides
    fun provideLearningOutboxDao(database: WordBookDatabase) = database.learningOutboxDao()

    @Provides
    fun provideWordStudyRecordDao(database: WordBookDatabase) = database.wordStudyRecordDao()

    @Provides
    fun provideWordDao(database: WordBookDatabase) = database.wordDao()

    @Provides
    fun provideWordDefinitionDao(database: WordBookDatabase) = database.wordDefinitionDao()

    @Provides
    fun provideWordExampleDao(database: WordBookDatabase) = database.wordExampleDao()

    @Provides
    fun provideWordFormDao(database: WordBookDatabase) = database.wordFormDao()

    @Provides
    fun provideWordUserMetaDao(database: WordBookDatabase) = database.wordUserMetaDao()

    @Provides
    fun provideWordRelationDao(database: WordBookDatabase) = database.wordRelationDao()

    @Provides
    fun provideWordRootDao(database: WordBookDatabase) = database.wordRootDao()

    @Provides
    fun provideRootTagDao(database: WordBookDatabase) = database.rootTagDao()

    @Provides
    fun provideRootWordDao(database: WordBookDatabase) = database.rootWordDao()

    @Provides
    @Singleton
    fun provideWordBookApiService(retrofit: Retrofit): WordBookApiService {
        return retrofit.create(WordBookApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideUserDataSyncApiService(retrofit: Retrofit): UserDataSyncApiService {
        return retrofit.create(UserDataSyncApiService::class.java)
    }

}
