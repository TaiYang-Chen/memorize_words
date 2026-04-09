package com.chen.memorizewords.domain.auth

import kotlinx.coroutines.flow.Flow

interface SessionKickoutNotifier {
    val events: Flow<Unit>
    suspend fun notifyKickout()
}
