package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class DirectSyncLauncherTest {

    @Test
    fun `session reset cancels active direct uploads`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val launcher = DirectSyncLauncher(scope)
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        try {
            launcher.launch<Unit>(
                operation = "test_upload",
                orderingKey = "test",
                request = {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
                }
            )

            withTimeout(2_000L) { started.await() }
            launcher.cancelAll()
            withTimeout(2_000L) { cancelled.await() }

            assertTrue(cancelled.isCompleted)
        } finally {
            scope.cancel()
        }
    }
}
