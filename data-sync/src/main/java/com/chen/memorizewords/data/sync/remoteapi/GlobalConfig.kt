package com.chen.memorizewords.data.sync.remoteapi

import com.chen.memorizewords.data.BuildConfig

object GlobalConfig {
    var isDebug: Boolean = BuildConfig.DEBUG
    var baseUrl: String = BuildConfig.API_BASE_URL
    var enableBodyLogging: Boolean = BuildConfig.DEBUG && BuildConfig.ENABLE_NETWORK_BODY_LOGGING
}
