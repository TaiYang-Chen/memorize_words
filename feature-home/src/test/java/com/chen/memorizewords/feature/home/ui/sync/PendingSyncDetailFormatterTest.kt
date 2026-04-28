package com.chen.memorizewords.feature.home.ui.sync

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSyncDetailFormatterTest {

    private val formatter = PendingSyncDetailFormatter(Gson())

    @Test
    fun `parsePayload builds business fields for practice session`() {
        val result = formatter.parsePayload(
            bizType = "PRACTICE_SESSION",
            payload = """
                {
                  "id": 7,
                  "date": "2026-04-25",
                  "mode": "LISTENING",
                  "entryType": "RANDOM",
                  "entryCount": 10,
                  "durationMs": 61000,
                  "createdAt": 1714000000000,
                  "wordIds": [1, 2, 3],
                  "questionCount": 8,
                  "completedCount": 6,
                  "correctCount": 5,
                  "submitCount": 6
                }
            """.trimIndent()
        )

        assertEquals("", result.message)
        assertTrue(result.fields.any { it.label == "\u8bb0\u5f55 ID" && it.value == "7" })
        assertTrue(
            result.fields.any {
                it.label == "\u5355\u8bcd ID \u5217\u8868" && it.value == "1, 2, 3"
            }
        )
    }

    @Test
    fun `parsePayload builds business fields for study plan`() {
        val result = formatter.parsePayload(
            bizType = "STUDY_PLAN",
            payload = """
                {
                  "dailyNewWords": 20,
                  "dailyReviewWords": 30,
                  "testMode": "MEANING_CHOICE",
                  "wordOrderType": "RANDOM"
                }
            """.trimIndent()
        )

        assertEquals("", result.message)
        assertTrue(
            result.fields.any { it.label == "\u6bcf\u65e5\u65b0\u8bcd\u6570" && it.value == "20" }
        )
        assertTrue(
            result.fields.any {
                it.label == "\u6d4b\u8bd5\u6a21\u5f0f" && it.value == "MEANING_CHOICE"
            }
        )
    }

    @Test
    fun `parsePayload falls back to raw payload message when json is invalid`() {
        val result = formatter.parsePayload(
            bizType = "CHECKIN_RECORD",
            payload = "{bad json"
        )

        assertTrue(result.fields.isEmpty())
        assertEquals(
            "\u5b57\u6bb5\u89e3\u6790\u5931\u8d25\uff0c\u4ec5\u5c55\u793a\u539f\u59cb\u5185\u5bb9",
            result.message
        )
    }
}
