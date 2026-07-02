package com.chen.memorizewords.data.study.repository.record

import com.chen.memorizewords.domain.sync.OutboxCommand
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class StudyOutboxFlushTest {
    @Test
    fun `flush deletes pending commands only after enqueue succeeds`() = runBlocking {
        val command = command("study_record:2026-07-02:1:true")
        val deleted = mutableListOf<OutboxCommand>()

        val flushed = flushPendingOutboxCommands(
            loadCommands = { listOf(command) },
            enqueueCommands = {},
            deleteCommands = { deleted += it }
        )

        assertTrue(flushed)
        assertEquals(listOf(command), deleted)
    }

    @Test
    fun `flush keeps pending commands when enqueue fails`() = runBlocking {
        val command = command("daily_study_duration:2026-07-02")
        val deleted = mutableListOf<OutboxCommand>()

        val flushed = flushPendingOutboxCommands(
            loadCommands = { listOf(command) },
            enqueueCommands = { error("sync db unavailable") },
            deleteCommands = { deleted += it }
        )

        assertFalse(flushed)
        assertTrue(deleted.isEmpty())
    }

    private fun command(key: String): OutboxCommand {
        return OutboxCommand(
            topic = OutboxTopic.STUDY_RECORD,
            key = key,
            operation = SyncOperation.UPSERT,
            payload = "{}",
            updatedAtEpochMillis = 1L
        )
    }
}
