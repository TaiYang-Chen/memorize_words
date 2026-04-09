package com.chen.memorizewords.network.constants

object NetworkConstants {
    const val CACHE_DIR_NAME = "http-cache"
    const val CACHE_SIZE = 10L * 1024L * 1024L // 10 MB
    const val CONNECTION_POOL_MAX_IDLE = 5
    const val CONNECTION_POOL_KEEP_ALIVE = 5L
    const val TIMEOUT_SECONDS = 30L
}
