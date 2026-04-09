package com.chen.memorizewords.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chen.memorizewords.data.local.room.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "memorize_words.db"
        )
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addMigrations(MIGRATION_8_9)
            .addMigrations(MIGRATION_9_10)
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

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `check_in_record` (
                    `date` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `signed_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`date`)
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `daily_practice_duration` (
                    `date` TEXT NOT NULL,
                    `total_duration_ms` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`date`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `practice_session_record` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `date` TEXT NOT NULL,
                    `mode` TEXT NOT NULL,
                    `entry_type` TEXT NOT NULL,
                    `entry_count` INTEGER NOT NULL,
                    `duration_ms` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `word_ids` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_practice_session_record_date`
                ON `practice_session_record` (`date`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_practice_session_record_created_at`
                ON `practice_session_record` (`created_at`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_outbox` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `biz_type` TEXT NOT NULL,
                    `biz_key` TEXT NOT NULL,
                    `operation` TEXT NOT NULL,
                    `payload` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `retry_count` INTEGER NOT NULL,
                    `last_error` TEXT,
                    `updated_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_biz_key`
                ON `sync_outbox` (`biz_key`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_sync_outbox_state`
                ON `sync_outbox` (`state`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_sync_outbox_updated_at`
                ON `sync_outbox` (`updated_at`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `floating_word_display_record` (
                    `date` TEXT NOT NULL,
                    `display_count` INTEGER NOT NULL,
                    `word_ids` TEXT NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`date`)
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE `practice_session_record`
                ADD COLUMN `question_count` INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            db.execSQL(
                """
                ALTER TABLE `practice_session_record`
                ADD COLUMN `completed_count` INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            db.execSQL(
                """
                ALTER TABLE `practice_session_record`
                ADD COLUMN `correct_count` INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            db.execSQL(
                """
                ALTER TABLE `practice_session_record`
                ADD COLUMN `submit_count` INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE `word_book`
                ADD COLUMN `content_version` INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `word_book_sync_state` (
                    `book_id` INTEGER NOT NULL,
                    `local_version` INTEGER NOT NULL,
                    `remote_version` INTEGER NOT NULL,
                    `ignored_version` INTEGER NOT NULL,
                    `last_prompted_version` INTEGER NOT NULL,
                    `last_checked_at` INTEGER NOT NULL,
                    PRIMARY KEY(`book_id`)
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE `word_book_sync_state`
                ADD COLUMN `pending_target_version` INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            db.execSQL(
                """
                ALTER TABLE `word_book_sync_state`
                ADD COLUMN `deferred_until` INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            db.execSQL(
                """
                ALTER TABLE `word_book_sync_state`
                ADD COLUMN `last_prompt_at` INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            db.execSQL(
                """
                ALTER TABLE `word_book_sync_state`
                ADD COLUMN `last_prompt_source` TEXT
                """.trimIndent()
            )
            db.execSQL(
                """
                ALTER TABLE `word_book_sync_state`
                ADD COLUMN `last_completed_at` INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            db.execSQL(
                """
                ALTER TABLE `word_book_sync_state`
                ADD COLUMN `last_failure_reason` TEXT
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `word_book_sync_state_new` (
                    `book_id` INTEGER NOT NULL,
                    `local_version` INTEGER NOT NULL,
                    `remote_version` INTEGER NOT NULL,
                    `pending_target_version` INTEGER NOT NULL,
                    `ignored_version` INTEGER NOT NULL,
                    `last_prompted_version` INTEGER NOT NULL,
                    `deferred_until` INTEGER NOT NULL,
                    `last_prompt_at` INTEGER NOT NULL,
                    `last_prompt_source` TEXT,
                    `last_checked_at` INTEGER NOT NULL,
                    `last_completed_at` INTEGER NOT NULL,
                    `last_failure_reason` TEXT,
                    PRIMARY KEY(`book_id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `word_book_sync_state_new` (
                    `book_id`,
                    `local_version`,
                    `remote_version`,
                    `pending_target_version`,
                    `ignored_version`,
                    `last_prompted_version`,
                    `deferred_until`,
                    `last_prompt_at`,
                    `last_prompt_source`,
                    `last_checked_at`,
                    `last_completed_at`,
                    `last_failure_reason`
                )
                SELECT
                    `book_id`,
                    `local_version`,
                    `remote_version`,
                    `pending_target_version`,
                    `ignored_version`,
                    `last_prompted_version`,
                    `deferred_until`,
                    `last_prompt_at`,
                    `last_prompt_source`,
                    `last_checked_at`,
                    `last_completed_at`,
                    `last_failure_reason`
                FROM `word_book_sync_state`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `word_book_sync_state`")
            db.execSQL("ALTER TABLE `word_book_sync_state_new` RENAME TO `word_book_sync_state`")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exam_practice_word_meta` (
                    `word_id` INTEGER NOT NULL,
                    `word` TEXT NOT NULL,
                    `total_count` INTEGER NOT NULL,
                    `favorite_count` INTEGER NOT NULL,
                    `wrong_count` INTEGER NOT NULL,
                    `objective_count` INTEGER NOT NULL,
                    `cached_at` INTEGER NOT NULL,
                    PRIMARY KEY(`word_id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exam_practice_item` (
                    `id` INTEGER NOT NULL,
                    `word_id` INTEGER NOT NULL,
                    `question_type` TEXT NOT NULL,
                    `exam_category` TEXT NOT NULL,
                    `paper_name` TEXT NOT NULL,
                    `difficulty_level` INTEGER NOT NULL,
                    `sort_order` INTEGER NOT NULL,
                    `group_key` TEXT,
                    `content_text` TEXT NOT NULL,
                    `context_text` TEXT,
                    `options` TEXT NOT NULL,
                    `answers` TEXT NOT NULL,
                    `left_items` TEXT NOT NULL,
                    `right_items` TEXT NOT NULL,
                    `answer_indexes` TEXT NOT NULL,
                    `analysis_text` TEXT,
                    `cached_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_exam_practice_item_word_id_sort_order`
                ON `exam_practice_item` (`word_id`, `sort_order`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_exam_practice_item_group_key`
                ON `exam_practice_item` (`group_key`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exam_practice_item_state` (
                    `exam_item_id` INTEGER NOT NULL,
                    `favorite` INTEGER NOT NULL,
                    `wrong_book` INTEGER NOT NULL,
                    `attempt_count` INTEGER NOT NULL,
                    `correct_count` INTEGER NOT NULL,
                    `last_result` TEXT,
                    `last_answered_at` INTEGER,
                    PRIMARY KEY(`exam_item_id`)
                )
                """.trimIndent()
            )
        }
    }
}
