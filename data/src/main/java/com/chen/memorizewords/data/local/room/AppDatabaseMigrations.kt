package com.chen.memorizewords.data.local.room

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson

object AppDatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            val gson = Gson()
            database.execSQL("PRAGMA foreign_keys=OFF")

            migrateWordBook(database)
            migrateCurrentWordBookSelection(database)
            migrateWordBookSyncState(database)
            migrateWordBookWords(database)
            migrateWordBookProgress(database)

            migrateWordDefinitions(database)
            migrateWordExamples(database)
            migrateWordForms(database)
            migrateWordUserMeta(database)
            migrateWordRelations(database)
            migrateWordFavorite(database)

            dropLegacyRootTags(database)
            migrateRootMeanings(database)
            migrateRootExamples(database)
            migrateRootVariants(database)
            migrateRootWordRelation(database)

            migrateWordLearningState(database)
            migrateWordStudyRecords(database)

            migrateExamPractice(database)
            migratePracticeSessions(database, gson)
            migrateFloatingDisplayRecords(database, gson)
            migrateSyncOutbox(database)
            installRuntimeGuards(database)

            database.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("PRAGMA foreign_keys=OFF")

            migrateWordFavoriteToPreciseTimestamps(database)
            migrateWordStudyRecordsToHistorySnapshots(database)
            migratePracticeSessionsToEnums(database)
            installRuntimeGuards(database)

            database.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("PRAGMA foreign_keys=OFF")

            migrateWordFavoriteToSingleTimestamp(database)
            migratePracticeSessionWordsToHistoricalReferences(database)
            migrateFloatingDisplayWordsToHistoricalReferences(database)
            installRuntimeGuards(database)

            database.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("PRAGMA foreign_keys=OFF")

            migrateDailyStudyDurationToValidatedSchema(database)
            migrateDailyPracticeDurationToValidatedSchema(database)
            migrateFloatingDisplayRecordToValidatedSchema(database)
            migrateWordBookSyncStateToValidatedSchema(database)
            migrateWordRootsToNormalizedTags(database)
            installRuntimeGuards(database)

            database.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    fun installRuntimeGuards(database: SupportSQLiteDatabase) {
        installCurrentWordBookSelectionGuard(database)
        installDailyStudyDurationGuard(database)
        installDailyPracticeDurationGuard(database)
        installFloatingWordDisplayRecordGuard(database)
        installWordBookProgressGuard(database)
        installWordLearningStateGuard(database)
        installWordBookSyncStateGuard(database)
        installCheckInGuard(database)
        installExamPracticeItemGuard(database)
        installExamPracticeItemStateGuard(database)
        installPracticeSessionRecordGuard(database)
        installSyncOutboxGuard(database)
    }

    private fun migrateWordBook(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_book RENAME TO word_book_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_book (
                id INTEGER NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                image TEXT NOT NULL,
                description TEXT NOT NULL,
                total_words INTEGER NOT NULL,
                content_version INTEGER NOT NULL,
                is_new INTEGER NOT NULL,
                is_hot INTEGER NOT NULL,
                is_public INTEGER NOT NULL,
                created_by_user_id TEXT,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO word_book (
                id,
                name,
                category,
                image,
                description,
                total_words,
                content_version,
                is_new,
                is_hot,
                is_public,
                created_by_user_id
            )
            SELECT
                id,
                name,
                category,
                image,
                description,
                total_words,
                content_version,
                is_new,
                is_hot,
                is_public,
                created_by_user_id
            FROM word_book_old
            """.trimIndent()
        )
    }

    private fun migrateCurrentWordBookSelection(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS current_word_book_selection (
                selection_id INTEGER NOT NULL,
                book_id INTEGER NOT NULL,
                CHECK(selection_id = 1),
                PRIMARY KEY(selection_id),
                FOREIGN KEY(book_id) REFERENCES word_book(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_current_word_book_selection_book_id
            ON current_word_book_selection(book_id)
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO current_word_book_selection(selection_id, book_id)
            SELECT 1, id
            FROM word_book_old
            WHERE is_selected = 1
            ORDER BY id
            LIMIT 1
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_book_old")
    }

    private fun migrateWordBookSyncState(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_book_sync_state RENAME TO word_book_sync_state_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_book_sync_state (
                book_id INTEGER NOT NULL,
                local_version INTEGER NOT NULL,
                remote_version INTEGER NOT NULL,
                pending_target_version INTEGER,
                ignored_version INTEGER,
                last_prompted_version INTEGER,
                deferred_until INTEGER,
                last_prompt_at INTEGER,
                last_prompt_source TEXT,
                last_checked_at INTEGER,
                last_completed_at INTEGER,
                last_failure_reason TEXT,
                PRIMARY KEY(book_id),
                FOREIGN KEY(book_id) REFERENCES word_book(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO word_book_sync_state (
                book_id,
                local_version,
                remote_version,
                pending_target_version,
                ignored_version,
                last_prompted_version,
                deferred_until,
                last_prompt_at,
                last_prompt_source,
                last_checked_at,
                last_completed_at,
                last_failure_reason
            )
            SELECT
                book_id,
                local_version,
                remote_version,
                NULLIF(pending_target_version, 0),
                NULLIF(ignored_version, 0),
                NULLIF(last_prompted_version, 0),
                NULLIF(deferred_until, 0),
                NULLIF(last_prompt_at, 0),
                last_prompt_source,
                NULLIF(last_checked_at, 0),
                NULLIF(last_completed_at, 0),
                last_failure_reason
            FROM word_book_sync_state_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_book_sync_state_old")
    }

    private fun migrateWordBookWords(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_book_words RENAME TO word_book_words_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_book_words (
                word_book_id INTEGER NOT NULL,
                word_id INTEGER NOT NULL,
                PRIMARY KEY(word_book_id, word_id),
                FOREIGN KEY(word_book_id) REFERENCES word_book(id) ON DELETE CASCADE,
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_book_words_word_book_id ON word_book_words(word_book_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_book_words_word_id ON word_book_words(word_id)")
        database.execSQL(
            """
            INSERT INTO word_book_words(word_book_id, word_id)
            SELECT word_book_id, word_id
            FROM word_book_words_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_book_words_old")
    }

    private fun migrateWordBookProgress(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_book_progress RENAME TO word_book_progress_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_book_progress (
                book_id INTEGER NOT NULL,
                correct_count INTEGER NOT NULL,
                wrong_count INTEGER NOT NULL,
                study_day_count INTEGER NOT NULL,
                last_study_date TEXT,
                PRIMARY KEY(book_id),
                FOREIGN KEY(book_id) REFERENCES word_book(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO word_book_progress (
                book_id,
                correct_count,
                wrong_count,
                study_day_count,
                last_study_date
            )
            SELECT
                book_id,
                correct_count,
                wrong_count,
                study_day_count,
                NULLIF(last_study_date, '')
            FROM word_book_progress_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_book_progress_old")
    }

    private fun migrateWordDefinitions(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_definitions RENAME TO word_definitions_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_definitions (
                id INTEGER NOT NULL,
                word_id INTEGER NOT NULL,
                part_of_speech TEXT NOT NULL,
                meaning_chinese TEXT NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_definitions_word_id ON word_definitions(word_id)")
        database.execSQL(
            """
            INSERT INTO word_definitions(id, word_id, part_of_speech, meaning_chinese)
            SELECT id, word_id, part_of_speech, meaning_chinese
            FROM word_definitions_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_definitions_old")
    }

    private fun migrateWordExamples(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_examples RENAME TO word_examples_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_examples (
                id INTEGER NOT NULL,
                word_id INTEGER NOT NULL,
                definition_id INTEGER,
                english_sentence TEXT NOT NULL,
                chinese_translation TEXT,
                difficulty_level TEXT NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE,
                FOREIGN KEY(definition_id) REFERENCES word_definitions(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_examples_word_id ON word_examples(word_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_examples_definition_id ON word_examples(definition_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_examples_difficulty_level ON word_examples(difficulty_level)")
        database.execSQL(
            """
            INSERT INTO word_examples(
                id,
                word_id,
                definition_id,
                english_sentence,
                chinese_translation,
                difficulty_level
            )
            SELECT
                id,
                word_id,
                definition_id,
                english_sentence,
                chinese_translation,
                difficulty_level
            FROM word_examples_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_examples_old")
    }

    private fun migrateWordForms(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_forms RENAME TO word_forms_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_forms (
                id INTEGER NOT NULL,
                word_id INTEGER NOT NULL,
                form_word_id INTEGER,
                form_type TEXT NOT NULL,
                form_text TEXT NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE,
                FOREIGN KEY(form_word_id) REFERENCES words(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_forms_word_id ON word_forms(word_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_forms_form_word_id ON word_forms(form_word_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_forms_form_type ON word_forms(form_type)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_forms_form_text ON word_forms(form_text)")
        database.execSQL(
            """
            INSERT INTO word_forms(id, word_id, form_word_id, form_type, form_text)
            SELECT id, word_id, form_word_id, form_type, form_text
            FROM word_forms_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_forms_old")
    }

    private fun migrateWordUserMeta(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_user_meta RENAME TO word_user_meta_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_user_meta (
                word_id INTEGER NOT NULL,
                memory_tip TEXT,
                mnemonic_image_url TEXT,
                notes TEXT,
                root_memory_tip TEXT,
                is_user_selected INTEGER NOT NULL,
                PRIMARY KEY(word_id),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO word_user_meta(
                word_id,
                memory_tip,
                mnemonic_image_url,
                notes,
                root_memory_tip,
                is_user_selected
            )
            SELECT
                word_id,
                memory_tip,
                mnemonic_image_url,
                notes,
                root_memory_tip,
                is_user_selected
            FROM word_user_meta_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_user_meta_old")
    }

    private fun migrateWordRelations(database: SupportSQLiteDatabase) {
        migrateRelationTable(database, "word_synonyms")
        migrateRelationTable(database, "word_antonyms")
        migrateRelationTable(database, "word_tags")
        migrateRelationTable(database, "word_associations")
    }

    private fun migrateRelationTable(database: SupportSQLiteDatabase, tableName: String) {
        val oldTable = "${tableName}_old"
        database.execSQL("ALTER TABLE $tableName RENAME TO $oldTable")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $tableName (
                word_id INTEGER NOT NULL,
                value TEXT NOT NULL,
                normalized_value TEXT NOT NULL,
                PRIMARY KEY(word_id, value),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_${tableName}_word_id ON $tableName(word_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_${tableName}_normalized_value ON $tableName(normalized_value)")
        database.execSQL(
            """
            INSERT INTO $tableName(word_id, value, normalized_value)
            SELECT word_id, value, normalized_value
            FROM $oldTable
            """.trimIndent()
        )
        database.execSQL("DROP TABLE $oldTable")
    }

    private fun migrateWordFavorite(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_favorite RENAME TO word_favorite_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_favorite (
                word_id INTEGER NOT NULL,
                added_date TEXT NOT NULL,
                PRIMARY KEY(word_id),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO word_favorite(word_id, added_date)
            SELECT word_id, added_date
            FROM word_favorite_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_favorite_old")
    }

    private fun dropLegacyRootTags(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS root_tags")
    }

    private fun migrateRootMeanings(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE root_meanings RENAME TO root_meanings_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS root_meanings (
                id INTEGER NOT NULL,
                root_id INTEGER NOT NULL,
                meaning TEXT NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(root_id) REFERENCES word_roots(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_root_meanings_root_id ON root_meanings(root_id)")
        database.execSQL(
            """
            INSERT INTO root_meanings(id, root_id, meaning)
            SELECT id, rootId, meaning
            FROM root_meanings_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE root_meanings_old")
    }

    private fun migrateRootExamples(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE root_examples RENAME TO root_examples_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS root_examples (
                id INTEGER NOT NULL,
                meaning_id INTEGER NOT NULL,
                exampleSentence TEXT NOT NULL,
                translation TEXT NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(meaning_id) REFERENCES root_meanings(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_root_examples_meaning_id ON root_examples(meaning_id)")
        database.execSQL(
            """
            INSERT INTO root_examples(id, meaning_id, exampleSentence, translation)
            SELECT id, meaningId, exampleSentence, translation
            FROM root_examples_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE root_examples_old")
    }

    private fun migrateRootVariants(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE root_variants RENAME TO root_variants_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS root_variants (
                id INTEGER NOT NULL,
                root_id INTEGER NOT NULL,
                variant TEXT NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(root_id) REFERENCES word_roots(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_root_variants_root_id ON root_variants(root_id)")
        database.execSQL(
            """
            INSERT INTO root_variants(id, root_id, variant)
            SELECT id, rootId, variant
            FROM root_variants_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE root_variants_old")
    }

    private fun migrateRootWordRelation(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_root_relation RENAME TO word_root_relation_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_root_relation (
                word_id INTEGER NOT NULL,
                root_id INTEGER NOT NULL,
                context TEXT NOT NULL,
                part_of_speech TEXT NOT NULL,
                sequence INTEGER NOT NULL,
                PRIMARY KEY(word_id, sequence),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE,
                FOREIGN KEY(root_id) REFERENCES word_roots(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_root_relation_word_id ON word_root_relation(word_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_root_relation_root_id ON word_root_relation(root_id)")
        database.execSQL(
            """
            INSERT INTO word_root_relation(word_id, root_id, context, part_of_speech, sequence)
            SELECT wordId, rootId, context, partOfSpeech, sequence
            FROM word_root_relation_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_root_relation_old")
    }

    private fun migrateWordLearningState(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_learning_state RENAME TO word_learning_state_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_learning_state (
                word_id INTEGER NOT NULL,
                book_id INTEGER NOT NULL,
                total_learn_count INTEGER NOT NULL,
                last_learn_time INTEGER NOT NULL,
                next_review_time INTEGER NOT NULL,
                mastery_level INTEGER NOT NULL,
                user_status INTEGER NOT NULL,
                interval INTEGER NOT NULL,
                repetition INTEGER NOT NULL,
                efactor REAL NOT NULL,
                PRIMARY KEY(word_id, book_id),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE,
                FOREIGN KEY(book_id) REFERENCES word_book(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_learning_state_word_id ON word_learning_state(word_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_learning_state_book_id ON word_learning_state(book_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_learning_state_next_review_time ON word_learning_state(next_review_time)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_learning_state_book_id_user_status ON word_learning_state(book_id, user_status)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_learning_state_book_id_next_review_time ON word_learning_state(book_id, next_review_time)")
        database.execSQL(
            """
            INSERT INTO word_learning_state(
                word_id,
                book_id,
                total_learn_count,
                last_learn_time,
                next_review_time,
                mastery_level,
                user_status,
                interval,
                repetition,
                efactor
            )
            SELECT
                word_id,
                book_id,
                MAX(total_learn_count, 0),
                MAX(last_learn_time, 0),
                MAX(next_review_time, 0),
                CASE
                    WHEN mastery_level < 0 THEN 0
                    WHEN mastery_level > 5 THEN 5
                    ELSE mastery_level
                END,
                CASE
                    WHEN user_status IN (0, 1, 2) THEN user_status
                    ELSE 0
                END,
                MAX(interval, 0),
                MAX(repetition, 0),
                MAX(efactor, 0.0)
            FROM word_learning_state_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_learning_state_old")
    }

    private fun migrateWordStudyRecords(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_study_records RENAME TO word_study_records_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_study_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date TEXT NOT NULL,
                word TEXT NOT NULL,
                word_id INTEGER NOT NULL,
                definition TEXT NOT NULL,
                is_new_word INTEGER NOT NULL,
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_word_study_records_date_word_id_is_new_word
            ON word_study_records(date, word_id, is_new_word)
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_study_records_word_id ON word_study_records(word_id)")
        database.execSQL(
            """
            INSERT INTO word_study_records(id, date, word, word_id, definition, is_new_word)
            SELECT id, date, word, word_id, definition, is_new_word
            FROM word_study_records_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_study_records_old")
    }

    private fun migrateExamPractice(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE exam_practice_word_meta RENAME TO exam_practice_word_meta_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exam_practice_word_meta (
                word_id INTEGER NOT NULL,
                word TEXT NOT NULL,
                total_count INTEGER NOT NULL,
                favorite_count INTEGER NOT NULL,
                wrong_count INTEGER NOT NULL,
                objective_count INTEGER NOT NULL,
                cached_at INTEGER NOT NULL,
                PRIMARY KEY(word_id),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO exam_practice_word_meta(
                word_id,
                word,
                total_count,
                favorite_count,
                wrong_count,
                objective_count,
                cached_at
            )
            SELECT
                word_id,
                word,
                total_count,
                favorite_count,
                wrong_count,
                objective_count,
                cached_at
            FROM exam_practice_word_meta_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE exam_practice_word_meta_old")

        database.execSQL("ALTER TABLE exam_practice_item RENAME TO exam_practice_item_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exam_practice_item (
                id INTEGER NOT NULL,
                word_id INTEGER NOT NULL,
                question_type TEXT NOT NULL,
                exam_category TEXT NOT NULL,
                paper_name TEXT NOT NULL,
                difficulty_level INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                group_key TEXT,
                content_text TEXT NOT NULL,
                context_text TEXT,
                options TEXT NOT NULL,
                answers TEXT NOT NULL,
                left_items TEXT NOT NULL,
                right_items TEXT NOT NULL,
                answer_indexes TEXT NOT NULL,
                analysis_text TEXT,
                cached_at INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exam_practice_item_word_id_sort_order ON exam_practice_item(word_id, sort_order)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exam_practice_item_group_key ON exam_practice_item(group_key)")
        database.execSQL(
            """
            INSERT INTO exam_practice_item(
                id,
                word_id,
                question_type,
                exam_category,
                paper_name,
                difficulty_level,
                sort_order,
                group_key,
                content_text,
                context_text,
                options,
                answers,
                left_items,
                right_items,
                answer_indexes,
                analysis_text,
                cached_at
            )
            SELECT
                id,
                word_id,
                question_type,
                exam_category,
                paper_name,
                difficulty_level,
                sort_order,
                group_key,
                content_text,
                context_text,
                options,
                answers,
                left_items,
                right_items,
                answer_indexes,
                analysis_text,
                cached_at
            FROM exam_practice_item_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE exam_practice_item_old")

        database.execSQL("ALTER TABLE exam_practice_item_state RENAME TO exam_practice_item_state_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exam_practice_item_state (
                exam_item_id INTEGER NOT NULL,
                favorite INTEGER NOT NULL,
                wrong_book INTEGER NOT NULL,
                attempt_count INTEGER NOT NULL,
                correct_count INTEGER NOT NULL,
                last_result TEXT,
                last_answered_at INTEGER,
                PRIMARY KEY(exam_item_id),
                FOREIGN KEY(exam_item_id) REFERENCES exam_practice_item(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO exam_practice_item_state(
                exam_item_id,
                favorite,
                wrong_book,
                attempt_count,
                correct_count,
                last_result,
                last_answered_at
            )
            SELECT
                exam_item_id,
                favorite,
                wrong_book,
                attempt_count,
                correct_count,
                last_result,
                last_answered_at
            FROM exam_practice_item_state_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE exam_practice_item_state_old")
    }

    private fun migratePracticeSessions(database: SupportSQLiteDatabase, gson: Gson) {
        database.execSQL("ALTER TABLE practice_session_record RENAME TO practice_session_record_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS practice_session_record (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date TEXT NOT NULL,
                mode TEXT NOT NULL,
                entry_type TEXT NOT NULL,
                entry_count INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                question_count INTEGER NOT NULL,
                completed_count INTEGER NOT NULL,
                correct_count INTEGER NOT NULL,
                submit_count INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_practice_session_record_date ON practice_session_record(date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_practice_session_record_created_at ON practice_session_record(created_at)")
        database.execSQL(
            """
            INSERT INTO practice_session_record(
                id,
                date,
                mode,
                entry_type,
                entry_count,
                duration_ms,
                created_at,
                question_count,
                completed_count,
                correct_count,
                submit_count
            )
            SELECT
                id,
                date,
                mode,
                entry_type,
                entry_count,
                duration_ms,
                created_at,
                question_count,
                completed_count,
                correct_count,
                submit_count
            FROM practice_session_record_old
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS practice_session_word (
                session_id INTEGER NOT NULL,
                sequence INTEGER NOT NULL,
                word_id INTEGER NOT NULL,
                PRIMARY KEY(session_id, sequence),
                FOREIGN KEY(session_id) REFERENCES practice_session_record(id) ON DELETE CASCADE,
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_practice_session_word_session_id ON practice_session_word(session_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_practice_session_word_word_id ON practice_session_word(word_id)")
        database.query("SELECT id, word_ids FROM practice_session_record_old").use { cursor ->
            while (cursor.moveToNext()) {
                val sessionId = cursor.getLong(0)
                val wordIds = parseLongList(cursor.getString(1), gson)
                wordIds.forEachIndexed { index, wordId ->
                    database.execSQL(
                        "INSERT INTO practice_session_word(session_id, sequence, word_id) VALUES (?, ?, ?)",
                        arrayOf(sessionId, index, wordId)
                    )
                }
            }
        }
        database.execSQL("DROP TABLE practice_session_record_old")
    }

    private fun migrateFloatingDisplayRecords(database: SupportSQLiteDatabase, gson: Gson) {
        database.execSQL("ALTER TABLE floating_word_display_record RENAME TO floating_word_display_record_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS floating_word_display_record (
                date TEXT NOT NULL,
                display_count INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(date)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO floating_word_display_record(date, display_count, updated_at)
            SELECT date, display_count, updated_at
            FROM floating_word_display_record_old
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS floating_word_display_word (
                record_date TEXT NOT NULL,
                sequence INTEGER NOT NULL,
                word_id INTEGER NOT NULL,
                PRIMARY KEY(record_date, sequence),
                FOREIGN KEY(record_date) REFERENCES floating_word_display_record(date) ON DELETE CASCADE,
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_floating_word_display_word_record_date ON floating_word_display_word(record_date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_floating_word_display_word_word_id ON floating_word_display_word(word_id)")
        database.query("SELECT date, word_ids FROM floating_word_display_record_old").use { cursor ->
            while (cursor.moveToNext()) {
                val date = cursor.getString(0)
                val wordIds = parseLongList(cursor.getString(1), gson)
                wordIds.forEachIndexed { index, wordId ->
                    database.execSQL(
                        "INSERT INTO floating_word_display_word(record_date, sequence, word_id) VALUES (?, ?, ?)",
                        arrayOf(date, index, wordId)
                    )
                }
            }
        }
        database.execSQL("DROP TABLE floating_word_display_record_old")
    }

    private fun migrateSyncOutbox(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE sync_outbox RENAME TO sync_outbox_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_outbox (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                biz_type TEXT NOT NULL,
                biz_key TEXT NOT NULL,
                operation TEXT NOT NULL,
                payload TEXT NOT NULL,
                state TEXT NOT NULL,
                retry_count INTEGER NOT NULL,
                last_error TEXT,
                failure_kind TEXT,
                last_attempt_at INTEGER NOT NULL,
                next_retry_at INTEGER NOT NULL,
                lease_token TEXT,
                lease_expires_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_biz_key ON sync_outbox(biz_key)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_biz_type_updated_at ON sync_outbox(biz_type, updated_at)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_state_next_retry_at_updated_at ON sync_outbox(state, next_retry_at, updated_at)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_state_lease_expires_at_updated_at ON sync_outbox(state, lease_expires_at, updated_at)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_lease_token ON sync_outbox(lease_token)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_updated_at ON sync_outbox(updated_at)")
        database.execSQL(
            """
            INSERT INTO sync_outbox(
                id,
                biz_type,
                biz_key,
                operation,
                payload,
                state,
                retry_count,
                last_error,
                failure_kind,
                last_attempt_at,
                next_retry_at,
                lease_token,
                lease_expires_at,
                updated_at
            )
            SELECT
                id,
                biz_type,
                biz_key,
                operation,
                payload,
                state,
                retry_count,
                last_error,
                failure_kind,
                last_attempt_at,
                next_retry_at,
                lease_token,
                lease_expires_at,
                updated_at
            FROM sync_outbox_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE sync_outbox_old")
    }

    private fun migrateDailyStudyDurationToValidatedSchema(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE daily_study_duration RENAME TO daily_study_duration_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_study_duration (
                date TEXT NOT NULL,
                total_duration_ms INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_new_plan_completed INTEGER NOT NULL,
                is_review_plan_completed INTEGER NOT NULL,
                PRIMARY KEY(date),
                CHECK(total_duration_ms >= 0),
                CHECK(updated_at >= 0)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO daily_study_duration(
                date,
                total_duration_ms,
                updated_at,
                is_new_plan_completed,
                is_review_plan_completed
            )
            SELECT
                date,
                MAX(total_duration_ms, 0),
                MAX(updated_at, 0),
                is_new_plan_completed,
                is_review_plan_completed
            FROM daily_study_duration_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE daily_study_duration_old")
    }

    private fun migrateDailyPracticeDurationToValidatedSchema(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE daily_practice_duration RENAME TO daily_practice_duration_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_practice_duration (
                date TEXT NOT NULL,
                total_duration_ms INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(date),
                CHECK(total_duration_ms >= 0),
                CHECK(updated_at >= 0)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO daily_practice_duration(date, total_duration_ms, updated_at)
            SELECT
                date,
                MAX(total_duration_ms, 0),
                MAX(updated_at, 0)
            FROM daily_practice_duration_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE daily_practice_duration_old")
    }

    private fun migrateFloatingDisplayRecordToValidatedSchema(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE floating_word_display_record RENAME TO floating_word_display_record_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS floating_word_display_record (
                date TEXT NOT NULL,
                display_count INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(date),
                CHECK(display_count >= 0),
                CHECK(updated_at >= 0)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO floating_word_display_record(date, display_count, updated_at)
            SELECT
                date,
                MAX(display_count, 0),
                MAX(updated_at, 0)
            FROM floating_word_display_record_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE floating_word_display_record_old")
    }

    private fun migrateWordBookSyncStateToValidatedSchema(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_book_sync_state RENAME TO word_book_sync_state_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_book_sync_state (
                book_id INTEGER NOT NULL,
                local_version INTEGER NOT NULL,
                remote_version INTEGER NOT NULL,
                pending_target_version INTEGER,
                ignored_version INTEGER,
                last_prompted_version INTEGER,
                deferred_until INTEGER,
                last_prompt_at INTEGER,
                last_prompt_source TEXT,
                last_checked_at INTEGER,
                last_completed_at INTEGER,
                last_failure_reason TEXT,
                PRIMARY KEY(book_id),
                FOREIGN KEY(book_id) REFERENCES word_book(id) ON DELETE CASCADE,
                CHECK(book_id > 0),
                CHECK(local_version >= 0),
                CHECK(remote_version >= 0),
                CHECK(pending_target_version IS NULL OR pending_target_version >= 0),
                CHECK(ignored_version IS NULL OR ignored_version >= 0),
                CHECK(last_prompted_version IS NULL OR last_prompted_version >= 0),
                CHECK(deferred_until IS NULL OR deferred_until >= 0),
                CHECK(last_prompt_at IS NULL OR last_prompt_at >= 0),
                CHECK(last_prompt_source IS NULL OR last_prompt_source IN ('FOREGROUND', 'WORDBOOK_PAGE')),
                CHECK(last_checked_at IS NULL OR last_checked_at >= 0),
                CHECK(last_completed_at IS NULL OR last_completed_at >= 0)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO word_book_sync_state (
                book_id,
                local_version,
                remote_version,
                pending_target_version,
                ignored_version,
                last_prompted_version,
                deferred_until,
                last_prompt_at,
                last_prompt_source,
                last_checked_at,
                last_completed_at,
                last_failure_reason
            )
            SELECT
                book_id,
                MAX(local_version, 0),
                MAX(remote_version, 0),
                CASE
                    WHEN pending_target_version IS NULL OR pending_target_version <= 0 THEN NULL
                    ELSE pending_target_version
                END,
                CASE
                    WHEN ignored_version IS NULL OR ignored_version <= 0 THEN NULL
                    ELSE ignored_version
                END,
                CASE
                    WHEN last_prompted_version IS NULL OR last_prompted_version <= 0 THEN NULL
                    ELSE last_prompted_version
                END,
                CASE
                    WHEN deferred_until IS NULL OR deferred_until <= 0 THEN NULL
                    ELSE deferred_until
                END,
                CASE
                    WHEN last_prompt_at IS NULL OR last_prompt_at <= 0 THEN NULL
                    ELSE last_prompt_at
                END,
                CASE
                    WHEN last_prompt_source IN ('FOREGROUND', 'WORDBOOK_PAGE') THEN last_prompt_source
                    ELSE NULL
                END,
                CASE
                    WHEN last_checked_at IS NULL OR last_checked_at <= 0 THEN NULL
                    ELSE last_checked_at
                END,
                CASE
                    WHEN last_completed_at IS NULL OR last_completed_at <= 0 THEN NULL
                    ELSE last_completed_at
                END,
                last_failure_reason
            FROM word_book_sync_state_old
            WHERE book_id > 0
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_book_sync_state_old")
    }

    private fun migrateWordRootsToNormalizedTags(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_roots RENAME TO word_roots_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_roots (
                id INTEGER NOT NULL,
                root_word TEXT NOT NULL,
                core_meaning TEXT NOT NULL,
                etymology TEXT,
                source_language TEXT NOT NULL,
                difficulty INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_word_roots_root_word
            ON word_roots(root_word)
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_word_roots_source_language
            ON word_roots(source_language)
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_word_roots_difficulty
            ON word_roots(difficulty)
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO word_roots(id, root_word, core_meaning, etymology, source_language, difficulty)
            SELECT id, root_word, core_meaning, etymology, source_language, difficulty
            FROM word_roots_old
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS root_tags (
                root_id INTEGER NOT NULL,
                value TEXT NOT NULL,
                normalized_value TEXT NOT NULL,
                PRIMARY KEY(root_id, value),
                FOREIGN KEY(root_id) REFERENCES word_roots(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_root_tags_root_id ON root_tags(root_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_root_tags_normalized_value ON root_tags(normalized_value)")
        database.query("SELECT id, tags FROM word_roots_old").use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val tagsIndex = cursor.getColumnIndexOrThrow("tags")
            while (cursor.moveToNext()) {
                val rootId = cursor.getLong(idIndex)
                parseRootTagValues(cursor.getString(tagsIndex)).forEach { value ->
                    database.execSQL(
                        """
                        INSERT OR REPLACE INTO root_tags(root_id, value, normalized_value)
                        VALUES (?, ?, ?)
                        """.trimIndent(),
                        arrayOf(rootId, value, value.lowercase())
                    )
                }
            }
        }
        database.execSQL("DROP TABLE word_roots_old")
    }

    private fun installCurrentWordBookSelectionGuard(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_current_word_book_selection_insert
            BEFORE INSERT ON current_word_book_selection
            WHEN NEW.selection_id <> 1
            BEGIN
                SELECT RAISE(ABORT, 'current_word_book_selection.selection_id must be 1');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_current_word_book_selection_update
            BEFORE UPDATE OF selection_id ON current_word_book_selection
            WHEN NEW.selection_id <> 1
            BEGIN
                SELECT RAISE(ABORT, 'current_word_book_selection.selection_id must be 1');
            END
            """.trimIndent()
        )
    }

    private fun installDailyStudyDurationGuard(database: SupportSQLiteDatabase) {
        val condition = "NEW.total_duration_ms < 0 OR NEW.updated_at < 0"
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_daily_study_duration_insert
            BEFORE INSERT ON daily_study_duration
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'daily_study_duration contains invalid values');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_daily_study_duration_update
            BEFORE UPDATE ON daily_study_duration
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'daily_study_duration contains invalid values');
            END
            """.trimIndent()
        )
    }

    private fun installDailyPracticeDurationGuard(database: SupportSQLiteDatabase) {
        val condition = "NEW.total_duration_ms < 0 OR NEW.updated_at < 0"
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_daily_practice_duration_insert
            BEFORE INSERT ON daily_practice_duration
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'daily_practice_duration contains invalid values');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_daily_practice_duration_update
            BEFORE UPDATE ON daily_practice_duration
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'daily_practice_duration contains invalid values');
            END
            """.trimIndent()
        )
    }

    private fun installFloatingWordDisplayRecordGuard(database: SupportSQLiteDatabase) {
        val condition = "NEW.display_count < 0 OR NEW.updated_at < 0"
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_floating_word_display_record_insert
            BEFORE INSERT ON floating_word_display_record
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'floating_word_display_record contains invalid values');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_floating_word_display_record_update
            BEFORE UPDATE ON floating_word_display_record
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'floating_word_display_record contains invalid values');
            END
            """.trimIndent()
        )
    }

    private fun installWordBookProgressGuard(database: SupportSQLiteDatabase) {
        val condition = """
            NEW.correct_count < 0 OR
            NEW.wrong_count < 0 OR
            NEW.study_day_count < 0 OR
            NEW.last_study_date = ''
        """.trimIndent()
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_word_book_progress_insert
            BEFORE INSERT ON word_book_progress
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'word_book_progress contains invalid values');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_word_book_progress_update
            BEFORE UPDATE ON word_book_progress
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'word_book_progress contains invalid values');
            END
            """.trimIndent()
        )
    }

    private fun installWordBookSyncStateGuard(database: SupportSQLiteDatabase) {
        val condition = """
            NEW.book_id <= 0 OR
            NEW.local_version < 0 OR
            NEW.remote_version < 0 OR
            (NEW.pending_target_version IS NOT NULL AND NEW.pending_target_version < 0) OR
            (NEW.ignored_version IS NOT NULL AND NEW.ignored_version < 0) OR
            (NEW.last_prompted_version IS NOT NULL AND NEW.last_prompted_version < 0) OR
            (NEW.deferred_until IS NOT NULL AND NEW.deferred_until < 0) OR
            (NEW.last_prompt_at IS NOT NULL AND NEW.last_prompt_at < 0) OR
            (NEW.last_prompt_source IS NOT NULL AND NEW.last_prompt_source NOT IN ('FOREGROUND', 'WORDBOOK_PAGE')) OR
            (NEW.last_checked_at IS NOT NULL AND NEW.last_checked_at < 0) OR
            (NEW.last_completed_at IS NOT NULL AND NEW.last_completed_at < 0)
        """.trimIndent()
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_word_book_sync_state_insert
            BEFORE INSERT ON word_book_sync_state
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'word_book_sync_state contains invalid values');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_word_book_sync_state_update
            BEFORE UPDATE ON word_book_sync_state
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'word_book_sync_state contains invalid values');
            END
            """.trimIndent()
        )
    }

    private fun installWordLearningStateGuard(database: SupportSQLiteDatabase) {
        val condition = """
            NEW.total_learn_count < 0 OR
            NEW.last_learn_time < 0 OR
            NEW.next_review_time < 0 OR
            NEW.mastery_level NOT BETWEEN 0 AND 5 OR
            NEW.user_status NOT IN (0, 1, 2) OR
            NEW.interval < 0 OR
            NEW.repetition < 0 OR
            NEW.efactor < 0
        """.trimIndent()
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_word_learning_state_insert
            BEFORE INSERT ON word_learning_state
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'word_learning_state contains invalid values');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_word_learning_state_update
            BEFORE UPDATE ON word_learning_state
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'word_learning_state contains invalid values');
            END
            """.trimIndent()
        )
    }

    private fun installCheckInGuard(database: SupportSQLiteDatabase) {
        val condition = "NEW.type NOT IN ('AUTO', 'MAKEUP') OR NEW.signed_at < 0 OR NEW.updated_at < 0"
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_check_in_record_insert
            BEFORE INSERT ON check_in_record
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'check_in_record contains invalid values');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_check_in_record_update
            BEFORE UPDATE ON check_in_record
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'check_in_record contains invalid values');
            END
            """.trimIndent()
        )
    }

    private fun installExamPracticeItemGuard(database: SupportSQLiteDatabase) {
        val condition = """
            NEW.question_type NOT IN ('SINGLE_CHOICE', 'CLOZE', 'MATCHING', 'PASSAGE', 'TRANSLATION') OR
            NEW.exam_category NOT IN ('CET4', 'CET6', 'POSTGRADUATE') OR
            NEW.difficulty_level < 0 OR
            NEW.sort_order < 0 OR
            NEW.cached_at < 0
        """.trimIndent()
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_exam_practice_item_insert
            BEFORE INSERT ON exam_practice_item
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'exam_practice_item contains invalid values');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_exam_practice_item_update
            BEFORE UPDATE ON exam_practice_item
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'exam_practice_item contains invalid values');
            END
            """.trimIndent()
        )
    }

    private fun installExamPracticeItemStateGuard(database: SupportSQLiteDatabase) {
        val condition = """
            (NEW.last_result IS NOT NULL AND NEW.last_result NOT IN ('CORRECT', 'WRONG', 'UNGRADED')) OR
            NEW.attempt_count < 0 OR
            NEW.correct_count < 0 OR
            (NEW.last_answered_at IS NOT NULL AND NEW.last_answered_at < 0)
        """.trimIndent()
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_exam_practice_item_state_insert
            BEFORE INSERT ON exam_practice_item_state
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'exam_practice_item_state contains invalid values');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_exam_practice_item_state_update
            BEFORE UPDATE ON exam_practice_item_state
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'exam_practice_item_state contains invalid values');
            END
            """.trimIndent()
        )
    }

    private fun installPracticeSessionRecordGuard(database: SupportSQLiteDatabase) {
        val condition = """
            NEW.mode NOT IN ('LISTENING', 'SHADOWING', 'SPELLING', 'AUDIO_LOOP', 'EXAM') OR
            NEW.entry_type NOT IN ('SELF', 'RANDOM') OR
            NEW.entry_count < 0 OR
            NEW.duration_ms < 0 OR
            NEW.created_at < 0 OR
            NEW.question_count < 0 OR
            NEW.completed_count < 0 OR
            NEW.correct_count < 0 OR
            NEW.submit_count < 0
        """.trimIndent()
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_practice_session_record_insert
            BEFORE INSERT ON practice_session_record
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'practice_session_record contains invalid values');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_practice_session_record_update
            BEFORE UPDATE ON practice_session_record
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'practice_session_record contains invalid values');
            END
            """.trimIndent()
        )
    }

    private fun installSyncOutboxGuard(database: SupportSQLiteDatabase) {
        val condition = """
            NEW.operation NOT IN ('UPSERT', 'DELETE') OR
            NEW.state NOT IN ('QUEUED', 'IN_FLIGHT', 'RETRY_WAITING', 'BLOCKED') OR
            (NEW.failure_kind IS NOT NULL AND NEW.failure_kind NOT IN ('NETWORK', 'AUTH', 'SERVER', 'RATE_LIMIT', 'CLIENT', 'UNKNOWN')) OR
            NEW.retry_count < 0 OR
            NEW.last_attempt_at < 0 OR
            NEW.next_retry_at < 0 OR
            NEW.lease_expires_at < 0 OR
            NEW.updated_at < 0
        """.trimIndent()
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_sync_outbox_insert
            BEFORE INSERT ON sync_outbox
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'sync_outbox contains invalid values');
            END
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_sync_outbox_update
            BEFORE UPDATE ON sync_outbox
            WHEN $condition
            BEGIN
                SELECT RAISE(ABORT, 'sync_outbox contains invalid values');
            END
            """.trimIndent()
        )
    }

    private fun parseLongList(raw: String?, gson: Gson): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson(raw, LongArray::class.java)?.toList() ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun parseRootTagValues(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .split(',', '，', ';', '；', '|', '、')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase() }
    }

    private fun migrateWordFavoriteToPreciseTimestamps(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_favorite RENAME TO word_favorite_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_favorite (
                word_id INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                added_date TEXT NOT NULL,
                PRIMARY KEY(word_id),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_favorite_added_at ON word_favorite(added_at)")
        database.execSQL(
            """
            INSERT INTO word_favorite(word_id, added_at, added_date)
            SELECT
                word_id,
                COALESCE(
                    CAST(strftime('%s', NULLIF(added_date, '') || ' 00:00:00') AS INTEGER) * 1000,
                    CAST(strftime('%s', 'now') AS INTEGER) * 1000
                ),
                COALESCE(NULLIF(added_date, ''), date('now', 'localtime'))
            FROM word_favorite_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_favorite_old")
    }

    private fun migrateWordFavoriteToSingleTimestamp(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_favorite RENAME TO word_favorite_old")
        database.execSQL("DROP INDEX IF EXISTS index_word_favorite_added_at")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_favorite (
                word_id INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                PRIMARY KEY(word_id),
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_favorite_added_at ON word_favorite(added_at)")
        database.execSQL(
            """
            INSERT INTO word_favorite(word_id, added_at)
            SELECT word_id, MAX(added_at, 0)
            FROM word_favorite_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_favorite_old")
    }

    private fun migrateWordStudyRecordsToHistorySnapshots(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE word_study_records RENAME TO word_study_records_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS word_study_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date TEXT NOT NULL,
                word TEXT NOT NULL,
                word_id INTEGER NOT NULL,
                definition TEXT NOT NULL,
                is_new_word INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_word_study_records_date_word_id_is_new_word
            ON word_study_records(date, word_id, is_new_word)
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_study_records_word_id ON word_study_records(word_id)")
        database.execSQL(
            """
            INSERT INTO word_study_records(id, date, word, word_id, definition, is_new_word)
            SELECT id, date, word, word_id, definition, is_new_word
            FROM word_study_records_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE word_study_records_old")
    }

    private fun migratePracticeSessionsToEnums(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE practice_session_word RENAME TO practice_session_word_old")
        database.execSQL("ALTER TABLE practice_session_record RENAME TO practice_session_record_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS practice_session_record (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date TEXT NOT NULL,
                mode TEXT NOT NULL,
                entry_type TEXT NOT NULL,
                entry_count INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                question_count INTEGER NOT NULL,
                completed_count INTEGER NOT NULL,
                correct_count INTEGER NOT NULL,
                submit_count INTEGER NOT NULL,
                CHECK(mode IN ('LISTENING', 'SHADOWING', 'SPELLING', 'AUDIO_LOOP', 'EXAM')),
                CHECK(entry_type IN ('SELF', 'RANDOM'))
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_practice_session_record_date ON practice_session_record(date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_practice_session_record_created_at ON practice_session_record(created_at)")
        database.execSQL(
            """
            INSERT INTO practice_session_record(
                id,
                date,
                mode,
                entry_type,
                entry_count,
                duration_ms,
                created_at,
                question_count,
                completed_count,
                correct_count,
                submit_count
            )
            SELECT
                id,
                date,
                CASE
                    WHEN mode IN ('LISTENING', 'SHADOWING', 'SPELLING', 'AUDIO_LOOP', 'EXAM') THEN mode
                    ELSE 'LISTENING'
                END,
                CASE
                    WHEN entry_type IN ('SELF', 'RANDOM') THEN entry_type
                    ELSE 'RANDOM'
                END,
                MAX(entry_count, 0),
                MAX(duration_ms, 0),
                MAX(created_at, 0),
                MAX(question_count, 0),
                MAX(completed_count, 0),
                MAX(correct_count, 0),
                MAX(submit_count, 0)
            FROM practice_session_record_old
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS practice_session_word (
                session_id INTEGER NOT NULL,
                sequence INTEGER NOT NULL,
                word_id INTEGER NOT NULL,
                PRIMARY KEY(session_id, sequence),
                FOREIGN KEY(session_id) REFERENCES practice_session_record(id) ON DELETE CASCADE,
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_practice_session_word_session_id ON practice_session_word(session_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_practice_session_word_word_id ON practice_session_word(word_id)")
        database.execSQL(
            """
            INSERT INTO practice_session_word(session_id, sequence, word_id)
            SELECT session_id, sequence, word_id
            FROM practice_session_word_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE practice_session_word_old")
        database.execSQL("DROP TABLE practice_session_record_old")
    }

    private fun migratePracticeSessionWordsToHistoricalReferences(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE practice_session_word RENAME TO practice_session_word_old")
        database.execSQL("DROP INDEX IF EXISTS index_practice_session_word_session_id")
        database.execSQL("DROP INDEX IF EXISTS index_practice_session_word_word_id")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS practice_session_word (
                session_id INTEGER NOT NULL,
                sequence INTEGER NOT NULL,
                word_id INTEGER NOT NULL,
                PRIMARY KEY(session_id, sequence),
                FOREIGN KEY(session_id) REFERENCES practice_session_record(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_practice_session_word_session_id ON practice_session_word(session_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_practice_session_word_word_id ON practice_session_word(word_id)")
        database.execSQL(
            """
            INSERT INTO practice_session_word(session_id, sequence, word_id)
            SELECT session_id, sequence, word_id
            FROM practice_session_word_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE practice_session_word_old")
    }

    private fun migrateFloatingDisplayWordsToHistoricalReferences(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE floating_word_display_word RENAME TO floating_word_display_word_old")
        database.execSQL("DROP INDEX IF EXISTS index_floating_word_display_word_record_date")
        database.execSQL("DROP INDEX IF EXISTS index_floating_word_display_word_word_id")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS floating_word_display_word (
                record_date TEXT NOT NULL,
                sequence INTEGER NOT NULL,
                word_id INTEGER NOT NULL,
                PRIMARY KEY(record_date, sequence),
                FOREIGN KEY(record_date) REFERENCES floating_word_display_record(date) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_floating_word_display_word_record_date ON floating_word_display_word(record_date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_floating_word_display_word_word_id ON floating_word_display_word(word_id)")
        database.execSQL(
            """
            INSERT INTO floating_word_display_word(record_date, sequence, word_id)
            SELECT record_date, sequence, word_id
            FROM floating_word_display_word_old
            """.trimIndent()
        )
        database.execSQL("DROP TABLE floating_word_display_word_old")
    }
}

private inline fun <T : Cursor?, R> T.use(block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        this?.close()
    }
}
