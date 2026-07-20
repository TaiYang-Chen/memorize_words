package com.chen.memorizewords.feature.floatingreview.ui.character

import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CharacterPackDeletionExecutorTest {
    @Test
    fun `selected pack disables and stops before delete`() = runBlocking {
        val events = mutableListOf<String>()
        var settingsReadCount = 0
        val executor = CharacterPackDeletionExecutor(
            getSettings = {
                events += "settings"
                if (settingsReadCount++ == 0) {
                    settings(selectedPackId = "pack-a", enabled = true)
                } else {
                    settings(selectedPackId = "pack-a", enabled = false)
                }
            },
            disableFloating = { events += "disable" },
            deleteInstalled = { packId -> events += "delete:$packId" },
            stopFloating = { events += "stop" }
        )

        executor.execute("pack-a")

        assertEquals(
            listOf("settings", "disable", "stop", "delete:pack-a", "settings"),
            events
        )
    }

    @Test
    fun `unselected pack deletes without disabling or stopping`() = runBlocking {
        val events = mutableListOf<String>()
        val executor = CharacterPackDeletionExecutor(
            getSettings = {
                events += "settings"
                settings(selectedPackId = "pack-b", enabled = true)
            },
            disableFloating = { events += "disable" },
            deleteInstalled = { packId -> events += "delete:$packId" },
            stopFloating = { events += "stop" }
        )

        executor.execute("pack-a")

        assertEquals(listOf("settings", "delete:pack-a", "settings"), events)
    }

    @Test
    fun `disable failure prevents selected pack deletion`() = runBlocking {
        val events = mutableListOf<String>()
        val executor = CharacterPackDeletionExecutor(
            getSettings = {
                events += "settings"
                settings(selectedPackId = "pack-a", enabled = true)
            },
            disableFloating = {
                events += "disable"
                throw IllegalStateException("disable failed")
            },
            deleteInstalled = { packId -> events += "delete:$packId" },
            stopFloating = { events += "stop" }
        )

        val failure = try {
            executor.execute("pack-a")
            null
        } catch (error: Throwable) {
            error
        }

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf("settings", "disable"), events)
    }

    @Test
    fun `delete failure keeps selected pack disabled and does not read post state`() = runBlocking {
        val events = mutableListOf<String>()
        val executor = CharacterPackDeletionExecutor(
            getSettings = {
                events += "settings"
                settings(selectedPackId = "pack-a", enabled = true)
            },
            disableFloating = { events += "disable" },
            deleteInstalled = { packId ->
                events += "delete:$packId"
                throw IllegalStateException("delete failed")
            },
            stopFloating = { events += "stop" }
        )

        val failure = try {
            executor.execute("pack-a")
            null
        } catch (error: Throwable) {
            error
        }

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf("settings", "disable", "stop", "delete:pack-a"), events)
    }

    @Test
    fun `concurrent enabled selection after delete is disabled and stopped`() = runBlocking {
        val events = mutableListOf<String>()
        var settingsReadCount = 0
        val executor = CharacterPackDeletionExecutor(
            getSettings = {
                events += "settings"
                if (settingsReadCount++ == 0) {
                    settings(selectedPackId = "pack-b", enabled = true)
                } else {
                    settings(selectedPackId = "pack-a", enabled = true)
                }
            },
            disableFloating = { events += "disable" },
            deleteInstalled = { packId -> events += "delete:$packId" },
            stopFloating = { events += "stop" }
        )

        executor.execute("pack-a")

        assertEquals(
            listOf("settings", "delete:pack-a", "settings", "disable", "stop"),
            events
        )
    }

    private fun settings(
        selectedPackId: String?,
        enabled: Boolean
    ): FloatingWordSettings = FloatingWordSettings(
        enabled = enabled,
        selectedCharacterPackId = selectedPackId
    )
}