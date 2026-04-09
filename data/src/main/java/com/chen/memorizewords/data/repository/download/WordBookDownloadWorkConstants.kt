package com.chen.memorizewords.data.repository.download

object WordBookDownloadWorkConstants {
    const val TAG_DOWNLOAD = "wordbook_download"
    const val TAG_BOOK_PREFIX = "wordbook_download_book_"
    const val WORK_NAME_PREFIX = "wordbook_download_"
    const val BOOTSTRAP_WORK_NAME = "bootstrap_wordbook_data"

    const val CHANNEL_ID = "wordbook_download"

    const val KEY_BOOK_ID = "book_id"
    const val KEY_BOOK_TITLE = "book_title"
    const val KEY_TOTAL_WORDS = "total_words"
    const val KEY_DOWNLOADED_WORDS = "downloaded_words"
    const val KEY_PROGRESS = "progress"
    const val KEY_ERROR_MESSAGE = "error_message"
    const val KEY_PAUSED_BOOK_IDS = "wordbook_paused_book_ids"
    const val KEY_REPORT_MY_BOOK = "report_my_book"
    const val KEY_FORCE_REFRESH = "force_refresh"
    const val KEY_TARGET_VERSION = "target_version"

    fun uniqueWorkName(bookId: Long): String = "$WORK_NAME_PREFIX$bookId"
    fun bookTag(bookId: Long): String = "$TAG_BOOK_PREFIX$bookId"
}
