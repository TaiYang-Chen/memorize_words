package com.chen.memorizewords.data.repository.download

object FileDownloadWorkConstants {
    const val KEY_TASK_ID = "task_id"
    const val KEY_URL = "url"
    const val KEY_FILE_NAME = "file_name"
    const val KEY_MIME_TYPE = "mime_type"
    const val KEY_DISPLAY_TITLE = "display_title"
    const val KEY_DISPLAY_DESC = "display_desc"
    const val KEY_DESTINATION_DIR = "destination_dir"
    const val KEY_COMPLETION_ACTION = "completion_action"
    const val KEY_FILE_PATH = "file_path"
    const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
    const val KEY_TOTAL_BYTES = "total_bytes"
    const val KEY_PROGRESS = "progress"
    const val KEY_ERROR_MESSAGE = "error_message"

    const val ACTION_PAUSE = "com.chen.memorizewords.data.download.ACTION_PAUSE"
    const val ACTION_RESUME = "com.chen.memorizewords.data.download.ACTION_RESUME"
    const val ACTION_CANCEL = "com.chen.memorizewords.data.download.ACTION_CANCEL"
    const val EXTRA_TASK_ID = "extra_task_id"

    const val CHANNEL_ID = "file_download"
    const val CHANNEL_NAME = "File download"

    fun uniqueWorkName(taskId: String): String = "download_$taskId"
    fun tag(taskId: String): String = "download_task_$taskId"
}

