package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingSettingsDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingSettingsSyncRequest
import com.chen.memorizewords.domain.sync.FloatingSettingsSyncPayload
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FloatingSettingsCompatibilityTest {

    @Test
    fun `legacy server response without character pack keeps no local selection`() {
        val adapter = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(FloatingSettingsDto::class.java)

        val dto = adapter.fromJson(
            """
            {
              "enabled": true,
              "sourceType": "CURRENT_BOOK",
              "orderType": "RANDOM",
              "fieldConfigs": [],
              "selectedWordIds": [],
              "floatingBallX": 0,
              "floatingBallY": 0,
              "autoStartOnBoot": false,
              "autoStartOnAppLaunch": true,
              "ballSizePercent": 60,
              "cardGapDp": -20
            }
            """.trimIndent()
        )

        assertNotNull(dto)
        assertNull(dto.selectedCharacterPackId)
    }

    @Test
    fun `legacy failed settings event without character pack remains replayable without selection`() {
        val adapter = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(FloatingSettingsSyncRequest::class.java)

        val request = adapter.fromJson(
            """
            {
              "enabled": true,
              "sourceType": "CURRENT_BOOK",
              "orderType": "RANDOM",
              "fieldConfigs": [],
              "selectedWordIds": [],
              "floatingBallX": 0,
              "floatingBallY": 0,
              "autoStartOnBoot": false,
              "autoStartOnAppLaunch": true,
              "ballSizePercent": 60,
              "cardOpacityPercent": 100,
              "cardGapDp": -20
            }
            """.trimIndent()
        )

        assertNotNull(request)
        assertNull(request.selectedCharacterPackId)
    }

    @Test
    fun `legacy outbox payload without character pack keeps no local selection`() {
        val payload = Gson().fromJson(
            """{"enabled":true}""",
            FloatingSettingsSyncPayload::class.java
        )

        assertNull(resolveFloatingCharacterPackId(payload.selectedCharacterPackId))
        assertNull(resolveFloatingCharacterPackId(" "))
        assertNull(resolveFloatingCharacterPackId("../unsafe"))
    }
}
