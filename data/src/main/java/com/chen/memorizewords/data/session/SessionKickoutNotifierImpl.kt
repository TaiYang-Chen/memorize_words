package com.chen.memorizewords.data.session

import com.chen.memorizewords.domain.auth.SessionKickoutNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionKickoutNotifierImpl : SessionKickoutNotifier {

    private val eventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val dedupeMutex = Mutex()
    private var lastKickoutAtMillis: Long = 0L

    override val events: Flow<Unit> = eventFlow.asSharedFlow()

    override suspend fun notifyKickout() {
        val shouldEmit = dedupeMutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastKickoutAtMillis < 1_000L) {
                false
            } else {
                lastKickoutAtMillis = now
                true
            }
        }
        if (shouldEmit) {
            eventFlow.emit(Unit)
        }
    }
}
