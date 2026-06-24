package com.chen.memorizewords.data.sync.repository.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class SyncAuthenticatedRequestSuccessReporterTest {

    @Test
    fun `authenticated success resumes retry waiting before scheduling immediate drain`() = runBlocking {
        val calls = mutableListOf<String>()
        val reporter = SyncAuthenticatedRequestSuccessReporter(
            syncOutboxRetryWaitResumer = FakeRetryWaitResumer(calls),
            syncOutboxDrainScheduler = FakeDrainScheduler(calls)
        )

        reporter.onAuthenticatedRequestSucceeded()

        assertEquals(listOf("resumeRetryWaiting", "scheduleImmediateDrain"), calls)
    }

    private class FakeRetryWaitResumer(
        private val calls: MutableList<String>
    ) : SyncOutboxRetryWaitResumer {
        override suspend fun resumeRetryWaiting(now: Long) {
            calls += "resumeRetryWaiting"
        }
    }

    private class FakeDrainScheduler(
        private val calls: MutableList<String>
    ) : SyncOutboxDrainScheduler {
        override fun scheduleDrain() {
            calls += "scheduleDrain"
        }

        override fun scheduleImmediateDrain() {
            calls += "scheduleImmediateDrain"
        }
    }
}
