package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class StaleBlockedWordBookDeleteOutboxCleanerTest {

    @Test
    fun `clean delegates to precise stale word book delete cleanup`() = runBlocking {
        val dao = FakeSyncOutboxDao()
        val cleaner = StaleBlockedWordBookDeleteOutboxCleaner(dao.proxy)

        cleaner.clean()

        assertEquals(1, dao.cleanupCallCount)
    }

    private class FakeSyncOutboxDao {
        var cleanupCallCount = 0

        val proxy: SyncOutboxDao = Proxy.newProxyInstance(
            SyncOutboxDao::class.java.classLoader,
            arrayOf(SyncOutboxDao::class.java),
            InvocationHandler { _, method, _ ->
                when (method.name) {
                    "deleteBlockedWordBookDeleteBookIdInvalid" -> {
                        cleanupCallCount += 1
                        1
                    }

                    else -> throw AssertionError("Unexpected DAO call: ${method.name}")
                }
            }
        ) as SyncOutboxDao
    }
}
