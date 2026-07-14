package com.chen.memorizewords.feature.home.ui.sync

import com.chen.memorizewords.domain.sync.FailureQueueEventType
import com.chen.memorizewords.domain.sync.model.SyncPendingRecord
import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingSyncDetailFormatterTest {

    @Test
    fun `new failure queue metadata is presented with user facing labels`() {
        val item = PendingSyncDetailFormatter(Gson()).toItemUi(
            record = SyncPendingRecord(
                id = "failed:event-1",
                sourceId = "failed",
                bizType = FailureQueueEventType.FAVORITE_REMOVE,
                bizKey = "favorite:42",
                operation = "LATEST",
                payload = "{\"wordId\":42}",
                state = "PENDING",
                retryCount = 0,
                lastError = null,
                failureKind = "NETWORK",
                lastAttemptAt = 0L,
                nextRetryAt = 0L,
                updatedAtMs = 1L
            ),
            isExpanded = false
        )

        assertEquals("\u53d6\u6d88\u6536\u85cf", item.bizTypeLabel)
        assertEquals("\u6392\u961f\u4e2d", item.stateLabel)
        assertEquals("\u4fdd\u7559\u6700\u65b0\u72b6\u6001", item.operationLabel)
        assertEquals("42", item.detailFields.single().value)
    }
}
