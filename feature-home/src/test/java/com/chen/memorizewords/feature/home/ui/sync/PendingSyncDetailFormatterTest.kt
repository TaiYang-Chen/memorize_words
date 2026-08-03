package com.chen.memorizewords.feature.home.ui.sync

import com.chen.memorizewords.domain.sync.FailureQueueEventType
import com.chen.memorizewords.domain.sync.model.SyncPendingRecord
import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingSyncDetailFormatterTest {

    @Test
    fun `floating settings details follow the current sync payload`() {
        val result = PendingSyncDetailFormatter(Gson()).parsePayload(
            bizType = FailureQueueEventType.FLOATING_SETTINGS,
            payload =
                """{"sourceType":"CURRENT_BOOK","orderType":"RANDOM","fieldConfigs":[],"selectedWordIds":[1,2],"ballSizePercent":80,"ballOpacityPercent":90,"cardOpacityPercent":70,"cardGapDp":12,"selectedCharacterPackId":"green_pet","floatingBallX":123}"""
        )

        assertEquals(
            listOf(
                "\u6765\u6e90\u7c7b\u578b",
                "\u6392\u5e8f\u65b9\u5f0f",
                "\u5b57\u6bb5\u914d\u7f6e",
                "\u5df2\u9009\u5355\u8bcd ID",
                "\u60ac\u6d6e\u7403\u5c3a\u5bf8",
                "\u60ac\u6d6e\u7403\u900f\u660e\u5ea6",
                "\u5361\u7247\u900f\u660e\u5ea6",
                "\u5c0f\u4eba\u4e0e\u5f39\u6846\u95f4\u9694",
                "\u89d2\u8272\u5305 ID"
            ),
            result.fields.map { it.label }
        )
    }

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
