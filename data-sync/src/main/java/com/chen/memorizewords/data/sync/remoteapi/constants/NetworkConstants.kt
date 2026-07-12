package com.chen.memorizewords.data.sync.remoteapi.constants

object NetworkConstants {
    const val CACHE_DIR_NAME = "http-cache"
    const val CACHE_SIZE = 10L * 1024L * 1024L // 10 MB
    const val CONNECTION_POOL_MAX_IDLE = 5
    const val CONNECTION_POOL_KEEP_ALIVE = 5L
    const val API_CONNECT_TIMEOUT_SECONDS = 5L
    const val API_READ_TIMEOUT_SECONDS = 15L
    const val API_WRITE_TIMEOUT_SECONDS = 15L
    const val DOWNLOAD_TIMEOUT_SECONDS = 30L
}
