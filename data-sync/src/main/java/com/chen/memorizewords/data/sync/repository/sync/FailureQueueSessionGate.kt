package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FailureQueueSessionToken internal constructor(
    val generation: Long
)

@Singleton
class FailureQueueSessionGate @Inject constructor(
    private val authStateProvider: AuthStateProvider
) {
    private val generation = AtomicLong(0L)
    private val mutex = Mutex()

    fun capture(): FailureQueueSessionToken? {
        if (!authStateProvider.isAuthenticated()) return null
        return FailureQueueSessionToken(generation.get())
    }

    fun isCurrent(token: FailureQueueSessionToken): Boolean {
        return authStateProvider.isAuthenticated() && token.generation == generation.get()
    }

    suspend fun <T> withCurrentSession(
        token: FailureQueueSessionToken,
        block: suspend () -> T
    ): T? = mutex.withLock {
        if (!isCurrent(token)) return@withLock null
        block()
    }

    suspend fun invalidateAndRun(block: suspend () -> Unit) {
        mutex.withLock {
            generation.incrementAndGet()
            block()
        }
    }
}
