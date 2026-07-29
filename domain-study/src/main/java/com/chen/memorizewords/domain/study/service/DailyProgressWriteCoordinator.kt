package com.chen.memorizewords.domain.study.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DailyProgressWriteCoordinator @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withWrite(block: suspend () -> T): T = mutex.withLock { block() }
}
