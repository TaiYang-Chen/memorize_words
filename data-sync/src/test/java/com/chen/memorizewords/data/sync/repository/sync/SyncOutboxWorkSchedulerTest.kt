package com.chen.memorizewords.data.sync.repository.sync

import androidx.work.NetworkType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SyncOutboxWorkSchedulerTest {

    @Test
    fun `drain request does not require WorkManager network constraint`() {
        val request = buildSyncOutboxDrainRequest(SyncWorkConstants.TAG_SYNC_OUTBOX_DRAIN)

        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(SyncWorkConstants.TAG_SYNC_OUTBOX_DRAIN in request.tags)
    }

    @Test
    fun `immediate drain uses one stable unique work name`() {
        assertEquals("work_sync_outbox_drain", SyncWorkConstants.WORK_SYNC_OUTBOX_DRAIN)
        assertEquals("work_sync_outbox_immediate_drain", SyncWorkConstants.WORK_SYNC_OUTBOX_IMMEDIATE_DRAIN)
        assertNotEquals(
            SyncWorkConstants.WORK_SYNC_OUTBOX_DRAIN,
            SyncWorkConstants.WORK_SYNC_OUTBOX_IMMEDIATE_DRAIN
        )
    }
}
