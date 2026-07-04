package com.chen.memorizewords.data.sync.repository.membership

import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MembershipStatusCacheTest {

    private val store = MemoryMembershipKeyValueStore()
    private val cache = MembershipStatusCache(store, Gson())

    @Test
    fun `status key includes user id`() {
        assertEquals("membership_status_42", cache.statusKey(42))
    }

    @Test
    fun `read and write status by user id without cross account pollution`() {
        val userOneStatus = MembershipStatus(
            level = "PRO",
            active = true,
            validUntilDate = "2026-06-24",
            remainingDays = 1,
            totalGrantedDays = 3,
            todayCheckedIn = true
        )
        val userTwoStatus = MembershipStatus(
            level = "PRO",
            active = false,
            validUntilDate = null,
            remainingDays = 0,
            totalGrantedDays = 0,
            todayCheckedIn = false
        )

        cache.write(1, userOneStatus)
        cache.write(2, userTwoStatus)

        assertEquals(userOneStatus, cache.read(1, currentDate = "2026-06-24"))
        assertEquals(userTwoStatus, cache.read(2))
    }

    @Test
    fun `read normalizes expired cached active status`() {
        cache.write(
            1,
            MembershipStatus(
                level = "PRO",
                active = true,
                validUntilDate = "2026-06-23",
                remainingDays = 1,
                totalGrantedDays = 3,
                todayCheckedIn = true
            )
        )

        val status = cache.read(1, currentDate = "2026-06-24")

        assertEquals(false, status?.active)
        assertEquals(0, status?.remainingDays)
        assertEquals("2026-06-23", status?.validUntilDate)
    }

    @Test
    fun `read keeps cached active status through valid until date`() {
        cache.write(
            1,
            MembershipStatus(
                level = "PRO",
                active = true,
                validUntilDate = "2026-06-24",
                remainingDays = 9,
                totalGrantedDays = 3,
                todayCheckedIn = true
            )
        )

        val status = cache.read(1, currentDate = "2026-06-24")

        assertEquals(true, status?.active)
        assertEquals(1, status?.remainingDays)
    }

    @Test
    fun `read normalizes invalid cached active date`() {
        cache.write(
            1,
            MembershipStatus(
                level = "PRO",
                active = true,
                validUntilDate = "2026-02-31",
                remainingDays = 1,
                totalGrantedDays = 3,
                todayCheckedIn = true
            )
        )

        val status = cache.read(1, currentDate = "2026-02-28")

        assertEquals(false, status?.active)
        assertEquals(0, status?.remainingDays)
    }

    @Test
    fun `read removes invalid cached json`() {
        val key = cache.statusKey(7)
        store.putString(key, "{")

        assertNull(cache.read(7))
        assertFalse(store.contains(key))
    }

    private class MemoryMembershipKeyValueStore : MembershipKeyValueStore {
        private val values = mutableMapOf<String, String>()

        override fun getString(key: String): String? = values[key]

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        fun contains(key: String): Boolean = values.containsKey(key)
    }
}
