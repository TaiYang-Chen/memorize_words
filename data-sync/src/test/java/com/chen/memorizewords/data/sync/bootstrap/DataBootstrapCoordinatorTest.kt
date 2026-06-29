package com.chen.memorizewords.data.sync.bootstrap

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import com.chen.memorizewords.data.sync.repository.sync.SyncWorkConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataBootstrapCoordinatorTest {

    @Test
    fun `normal bootstrap keeps network constraint`() {
        val request = buildDataBootstrapRequest()

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(SyncWorkConstants.TAG_DATA_BOOTSTRAP in request.tags)
        assertEquals(ExistingWorkPolicy.KEEP, DATA_BOOTSTRAP_POLICY)
    }

    @Test
    fun `post login bootstrap has no WorkManager network constraint`() {
        val request = buildPostLoginBootstrapRequest()

        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(SyncWorkConstants.TAG_POST_LOGIN_BOOTSTRAP in request.tags)
    }

    @Test
    fun `post login bootstrap replaces stale enqueued work`() {
        assertEquals(ExistingWorkPolicy.REPLACE, POST_LOGIN_BOOTSTRAP_POLICY)
    }
}
