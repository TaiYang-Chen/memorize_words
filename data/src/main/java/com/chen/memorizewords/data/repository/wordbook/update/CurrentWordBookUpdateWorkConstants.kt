package com.chen.memorizewords.data.repository.wordbook.update

object CurrentWordBookUpdateWorkConstants {
    const val TAG_UPDATE = "current_wordbook_update"
    const val TAG_BOOK_PREFIX = "current_wordbook_update_book_"
    const val WORK_NAME_PREFIX = "current_wordbook_update_"
    const val CHANNEL_ID = "current_wordbook_update"

    const val KEY_BOOK_ID = "book_id"
    const val KEY_BOOK_NAME = "book_name"
    const val KEY_TARGET_VERSION = "target_version"
    const val KEY_EXECUTION_MODE = "execution_mode"
    const val KEY_PROGRESS = "progress"
    const val KEY_ERROR_MESSAGE = "error_message"

    fun uniqueWorkName(bookId: Long): String = "$WORK_NAME_PREFIX$bookId"
    fun bookTag(bookId: Long): String = "$TAG_BOOK_PREFIX$bookId"
}
