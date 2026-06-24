package com.chen.memorizewords.data.practice.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PracticeDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
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
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_practice_item_word_id_sort_order` ON `exam_practice_item` (`word_id`, `sort_order`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_practice_item_group_key` ON `exam_practice_item` (`group_key`)")
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
                    PRIMARY KEY(`exam_item_id`),
                    FOREIGN KEY(`exam_item_id`) REFERENCES `exam_practice_item`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
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
                    `question_count` INTEGER NOT NULL,
                    `completed_count` INTEGER NOT NULL,
                    `correct_count` INTEGER NOT NULL,
                    `submit_count` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_session_record_date` ON `practice_session_record` (`date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_session_record_created_at` ON `practice_session_record` (`created_at`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `practice_session_word` (
                    `session_id` INTEGER NOT NULL,
                    `sequence` INTEGER NOT NULL,
                    `word_id` INTEGER NOT NULL,
                    PRIMARY KEY(`session_id`, `sequence`),
                    FOREIGN KEY(`session_id`) REFERENCES `practice_session_record`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_session_word_session_id` ON `practice_session_word` (`session_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_session_word_word_id` ON `practice_session_word` (`word_id`)")
        }
    }
}
