package com.chen.memorizewords

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingRuntimeProcessPolicyTest {

    @Test
    fun `remote floating process does not start main-process coordinators`() {
        assertFalse(shouldStartMainProcessStartupTasks("com.chen.memorizewords:floating"))
    }

    @Test
    fun `main app process starts main-process coordinators`() {
        assertTrue(shouldStartMainProcessStartupTasks("com.chen.memorizewords"))
    }
}
