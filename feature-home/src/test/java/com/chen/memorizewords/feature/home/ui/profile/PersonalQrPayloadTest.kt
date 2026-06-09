package com.chen.memorizewords.feature.home.ui.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalQrPayloadTest {

    @Test
    fun `create builds user identity link`() {
        val payload = PersonalQrPayload.create(userId = 42L, nickname = "Alice")

        assertTrue(payload.startsWith("myapp://user/card?"))
        assertTrue(payload.contains("uid=42"))
        assertTrue(payload.contains("name=Alice"))
    }

    @Test
    fun `create supports empty nickname`() {
        val payload = PersonalQrPayload.create(userId = 42L, nickname = null)
        val parsed = PersonalQrPayload.parse(payload)

        assertNotNull(parsed)
        assertEquals(42L, parsed.userId)
        assertEquals("", parsed.nickname)
    }

    @Test
    fun `create and parse support chinese nickname and spaces`() {
        val payload = PersonalQrPayload.create(userId = 86L, nickname = "小白 User")
        val parsed = PersonalQrPayload.parse(payload)

        assertNotNull(parsed)
        assertEquals(86L, parsed.userId)
        assertEquals("小白 User", parsed.nickname)
    }

    @Test
    fun `parse accepts valid user card link`() {
        val parsed = PersonalQrPayload.parse("myapp://user/card?uid=100&name=Bob")

        assertNotNull(parsed)
        assertEquals(100L, parsed.userId)
        assertEquals("Bob", parsed.nickname)
    }

    @Test
    fun `parse rejects non app qr content`() {
        assertNull(PersonalQrPayload.parse("https://example.com/user/card?uid=100&name=Bob"))
        assertNull(PersonalQrPayload.parse("myapp://word/card?uid=100&name=Bob"))
        assertNull(PersonalQrPayload.parse("myapp://user/card?uid=0&name=Bob"))
        assertNull(PersonalQrPayload.parse("plain text"))
    }
}
