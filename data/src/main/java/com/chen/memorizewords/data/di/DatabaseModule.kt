package com.chen.memorizewords.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.AppDatabaseMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val runtimeConstraintCallback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            AppDatabaseMigrations.installRuntimeGuards(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            AppDatabaseMigrations.installRuntimeGuards(db)
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "memorize_words.db"
        )
            .addMigrations(
                AppDatabaseMigrations.MIGRATION_1_2,
                AppDatabaseMigrations.MIGRATION_2_3,
                AppDatabaseMigrations.MIGRATION_3_4,
                AppDatabaseMigrations.MIGRATION_4_5
            )
            .addCallback(runtimeConstraintCallback)
            .enableMultiInstanceInvalidation()
            .build()
    }

    @Provides
    fun provideWordDao(appDatabase: AppDatabase) = appDatabase.wordDao()

    @Provides
    fun provideWordDefinitionDao(appDatabase: AppDatabase) = appDatabase.wordDefinitionDao()

    @Provides
    fun provideWordExampleDao(appDatabase: AppDatabase) = appDatabase.wordExampleDao()

    @Provides
    fun provideWordFormDao(appDatabase: AppDatabase) = appDatabase.wordFormDao()

    @Provides
    fun provideWordRelationDao(appDatabase: AppDatabase) = appDatabase.wordRelationDao()

    @Provides
    fun provideWordUserMetaDao(appDatabase: AppDatabase) = appDatabase.wordUserMetaDao()

    @Provides
    fun provideWordRootDao(appDatabase: AppDatabase) = appDatabase.wordRootDao()

    @Provides
    fun provideRootTagDao(appDatabase: AppDatabase) = appDatabase.rootTagDao()

    @Provides
    fun provideRootWordDao(appDatabase: AppDatabase) = appDatabase.rootWordDao()

    @Provides
    fun provideWordBookDao(appDatabase: AppDatabase) = appDatabase.wordBookDao()

    @Provides
    fun provideCurrentWordBookSelectionDao(appDatabase: AppDatabase) =
        appDatabase.currentWordBookSelectionDao()

    @Provides
    fun provideWordBookSyncStateDao(appDatabase: AppDatabase) = appDatabase.wordBookSyncStateDao()

    @Provides
    fun provideBookWordItemDao(appDatabase: AppDatabase) = appDatabase.wordBookItemDao()

    @Provides
    fun provideWordBookProgressDao(appDatabase: AppDatabase) = appDatabase.wordBookProgressDao()

    @Provides
    fun provideWordLearningStateDao(appDatabase: AppDatabase) = appDatabase.wordLearningStateDao()

    @Provides
    fun provideDailyStudyRecordsDao(appDatabase: AppDatabase) = appDatabase.dailyStudyRecordsDao()

    @Provides
    fun provideDailyStudyDurationDao(appDatabase: AppDatabase) = appDatabase.dailyStudyDurationDao()

    @Provides
    fun provideCheckInRecordDao(appDatabase: AppDatabase) = appDatabase.checkInRecordDao()

    @Provides
    fun provideDailyPracticeDurationDao(appDatabase: AppDatabase) = appDatabase.dailyPracticeDurationDao()

    @Provides
    fun provideExamPracticeDao(appDatabase: AppDatabase) = appDatabase.examPracticeDao()

    @Provides
    fun providePracticeSessionRecordDao(appDatabase: AppDatabase) = appDatabase.practiceSessionRecordDao()

    @Provides
    fun provideSyncOutboxDao(appDatabase: AppDatabase) = appDatabase.syncOutboxDao()

    @Provides
    fun provideWordFavoritesDao(appDatabase: AppDatabase) = appDatabase.wordFavoritesDao()

    @Provides
    fun provideFloatingWordDisplayRecordDao(appDatabase: AppDatabase) =
        appDatabase.floatingWordDisplayRecordDao()
}
