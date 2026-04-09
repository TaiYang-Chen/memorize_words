package com.chen.memorizewords.data.repository.sync

object SyncWorkConstants {
    const val UNIQUE_DATA_BOOTSTRAP = "data_bootstrap"
    const val UNIQUE_POST_LOGIN_BOOTSTRAP = "post_login_bootstrap"
    const val UNIQUE_SYNC_OUTBOX_DRAIN = "sync_outbox_drain"

    const val TAG_SYNC_OUTBOX_DRAIN = "sync_outbox_drain"
    const val TAG_DATA_BOOTSTRAP = "data_bootstrap"
    const val TAG_POST_LOGIN_BOOTSTRAP = "post_login_bootstrap"

    const val TAG_STUDY_PLAN_SYNC = "study_plan_sync"
    const val WORK_STUDY_PLAN_SYNC = "work_study_plan_sync"
    const val KEY_DAILY_NEW_WORDS = "daily_new_words"
    const val KEY_REVIEW_MULTIPLIER = "review_multiplier"
    const val KEY_TEST_MODE = "test_mode"
    const val KEY_WORD_ORDER_TYPE = "word_order_type"

    const val TAG_ADD_MY_WORD_BOOK = "add_my_word_book"
    private const val WORK_ADD_MY_WORD_BOOK_PREFIX = "work_add_my_word_book_"
    const val KEY_BOOK_ID = "book_id"

    fun addMyWordBookWorkName(bookId: Long): String = "$WORK_ADD_MY_WORD_BOOK_PREFIX$bookId"

    const val TAG_FAVORITE_SYNC = "favorite_sync"
    private const val WORK_FAVORITE_SYNC_PREFIX = "work_favorite_sync_"
    const val KEY_FAVORITE_ACTION = "favorite_action"
    const val ACTION_ADD_FAVORITE = "add_favorite"
    const val ACTION_REMOVE_FAVORITE = "remove_favorite"
    const val KEY_WORD_ID = "word_id"
    const val KEY_WORD = "word"
    const val KEY_DEFINITIONS = "definitions"
    const val KEY_PHONETIC = "phonetic"
    const val KEY_ADDED_DATE = "added_date"

    fun favoriteSyncWorkName(wordId: Long): String = "$WORK_FAVORITE_SYNC_PREFIX$wordId"

    const val TAG_WORD_STATE_SYNC = "word_state_sync"
    private const val WORK_WORD_STATE_SYNC_PREFIX = "work_word_state_sync_"
    const val KEY_WORD_STATE_ACTION = "word_state_action"
    const val ACTION_UPSERT_WORD_STATE = "upsert_word_state"
    const val ACTION_DELETE_WORD_STATES_BY_BOOK = "delete_word_states_by_book"
    const val KEY_TOTAL_LEARN_COUNT = "total_learn_count"
    const val KEY_LAST_LEARN_TIME = "last_learn_time"
    const val KEY_NEXT_REVIEW_TIME = "next_review_time"
    const val KEY_MASTERY_LEVEL = "mastery_level"
    const val KEY_USER_STATUS = "user_status"
    const val KEY_REPETITION = "repetition"
    const val KEY_INTERVAL = "interval"
    const val KEY_EFACTOR = "efactor"

    fun wordStateSyncWorkName(bookId: Long, wordId: Long): String =
        "$WORK_WORD_STATE_SYNC_PREFIX${bookId}_$wordId"

    fun wordStateDeleteByBookSyncWorkName(bookId: Long): String =
        "${WORK_WORD_STATE_SYNC_PREFIX}delete_$bookId"

    const val TAG_WORD_BOOK_PROGRESS_SYNC = "word_book_progress_sync"
    private const val WORK_WORD_BOOK_PROGRESS_SYNC_PREFIX = "work_word_book_progress_sync_"
    const val KEY_BOOK_NAME = "book_name"
    const val KEY_LEARNED_COUNT = "learned_count"
    const val KEY_MASTERED_COUNT = "mastered_count"
    const val KEY_TOTAL_COUNT = "total_count"
    const val KEY_CORRECT_COUNT = "correct_count"
    const val KEY_WRONG_COUNT = "wrong_count"
    const val KEY_STUDY_DAY_COUNT = "study_day_count"
    const val KEY_LAST_STUDY_DATE = "last_study_date"

    fun wordBookProgressSyncWorkName(bookId: Long): String =
        "$WORK_WORD_BOOK_PROGRESS_SYNC_PREFIX$bookId"

    const val TAG_WORD_STUDY_RECORD_SYNC = "word_study_record_sync"
    private const val WORK_WORD_STUDY_RECORD_SYNC_PREFIX = "work_word_study_record_sync_"
    const val KEY_DATE = "date"
    const val KEY_IS_NEW_WORD = "is_new_word"
    const val KEY_DEFINITION = "definition"

    fun wordStudyRecordSyncWorkName(date: String, wordId: Long, isNewWord: Boolean): String =
        "$WORK_WORD_STUDY_RECORD_SYNC_PREFIX${date}_${wordId}_${isNewWord}"

    const val TAG_DAILY_STUDY_DURATION_SYNC = "daily_study_duration_sync"
    private const val WORK_DAILY_STUDY_DURATION_SYNC_PREFIX = "work_daily_study_duration_sync_"
    const val KEY_TOTAL_DURATION_MS = "total_duration_ms"
    const val KEY_UPDATED_AT = "updated_at"
    const val KEY_IS_NEW_PLAN_COMPLETED = "is_new_plan_completed"
    const val KEY_IS_REVIEW_PLAN_COMPLETED = "is_review_plan_completed"

    fun dailyStudyDurationSyncWorkName(date: String): String =
        "$WORK_DAILY_STUDY_DURATION_SYNC_PREFIX$date"

    const val TAG_WORD_BOOK_SELECTION_SYNC = "word_book_selection_sync"
    const val WORK_WORD_BOOK_SELECTION_SYNC = "work_word_book_selection_sync"

    const val WORK_SYNC_OUTBOX_DRAIN = "work_sync_outbox_drain"
    const val WORK_POST_LOGIN_BOOTSTRAP = "work_post_login_bootstrap"

    const val TAG_PRACTICE_SETTINGS_SYNC = "practice_settings_sync"
    const val WORK_PRACTICE_SETTINGS_SYNC = "work_practice_settings_sync"
    const val KEY_PRACTICE_SELECTED_BOOK_ID = "practice_selected_book_id"
    const val KEY_PRACTICE_INTERVAL_SECONDS = "practice_interval_seconds"
    const val KEY_PRACTICE_LOOP_ENABLED = "practice_loop_enabled"
    const val KEY_PRACTICE_PLAY_WORD_SPELLING = "practice_play_word_spelling"
    const val KEY_PRACTICE_PLAY_CHINESE_MEANING = "practice_play_chinese_meaning"

    const val TAG_PRACTICE_DURATION_SYNC = "practice_duration_sync"
    private const val WORK_PRACTICE_DURATION_SYNC_PREFIX = "work_practice_duration_sync_"
    const val KEY_PRACTICE_DATE = "practice_date"

    fun practiceDurationSyncWorkName(date: String): String =
        "$WORK_PRACTICE_DURATION_SYNC_PREFIX$date"

    const val TAG_PRACTICE_SESSION_SYNC = "practice_session_sync"
    private const val WORK_PRACTICE_SESSION_SYNC_PREFIX = "work_practice_session_sync_"
    const val KEY_PRACTICE_SESSION_ID = "practice_session_id"

    fun practiceSessionSyncWorkName(recordId: Long): String =
        "$WORK_PRACTICE_SESSION_SYNC_PREFIX$recordId"

    const val TAG_FLOATING_SETTINGS_SYNC = "floating_settings_sync"
    const val WORK_FLOATING_SETTINGS_SYNC = "work_floating_settings_sync"
    const val KEY_FLOATING_ENABLED = "floating_enabled"
    const val KEY_FLOATING_SOURCE_TYPE = "floating_source_type"
    const val KEY_FLOATING_ORDER_TYPE = "floating_order_type"
    const val KEY_FLOATING_FIELD_CONFIGS = "floating_field_configs"
    const val KEY_FLOATING_SELECTED_WORD_IDS = "floating_selected_word_ids"
    const val KEY_FLOATING_BALL_X = "floating_ball_x"
    const val KEY_FLOATING_BALL_Y = "floating_ball_y"
    const val KEY_FLOATING_AUTO_START_ON_BOOT = "floating_auto_start_on_boot"
    const val KEY_FLOATING_AUTO_START_ON_APP_LAUNCH = "floating_auto_start_on_app_launch"
    const val KEY_FLOATING_CARD_OPACITY_PERCENT = "floating_card_opacity_percent"
    const val KEY_FLOATING_DOCK_CONFIG = "floating_dock_config"
    const val KEY_FLOATING_DOCK_STATE = "floating_dock_state"

    const val TAG_FLOATING_DISPLAY_RECORD_SYNC = "floating_display_record_sync"
    private const val WORK_FLOATING_DISPLAY_RECORD_SYNC_PREFIX = "work_floating_display_record_sync_"
    const val KEY_FLOATING_DATE = "floating_date"

    fun floatingDisplayRecordSyncWorkName(date: String): String =
        "$WORK_FLOATING_DISPLAY_RECORD_SYNC_PREFIX$date"
}
