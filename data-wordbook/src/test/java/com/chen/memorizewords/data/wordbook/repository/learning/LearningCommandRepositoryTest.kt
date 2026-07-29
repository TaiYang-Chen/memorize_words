package com.chen.memorizewords.data.wordbook.repository.learning

import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookEntity
import com.chen.memorizewords.data.wordbook.repository.WordBookTransactionRunner
import com.chen.memorizewords.domain.study.model.learning.LearningEventAction
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventCommand
import com.chen.memorizewords.domain.study.repository.learning.BookLearningWriteCoordinator
import com.chen.memorizewords.domain.study.repository.sync.LearningEventSyncPort
import com.chen.memorizewords.domain.sync.LearningEventSyncPayload
import com.chen.memorizewords.domain.word.model.word.Word
import com.google.gson.Gson
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LearningCommandRepositoryTest {

    @Test
    fun `study fact and frozen daily projection task commit in the same transaction`() = runBlocking {
        val transactionRunner = TrackingWordBookTransactionRunner()
        var event: com.chen.memorizewords.data.wordbook.local.room.model.learning.event.LearningEventEntity? = null
        var record: com.chen.memorizewords.data.wordbook.local.room.model.learning.record.WordStudyRecordEntity? = null
        var task: com.chen.memorizewords.data.wordbook.local.room.model.learning.projection.DailyProgressProjectionTaskEntity? = null
        val syncPort = RecordingLearningEventSyncPort()
        val repository = LearningCommandRepository(
            transactionRunner = transactionRunner,
            learningEventDao = proxy { methodName, args ->
                when (methodName) {
                    "getMaxClientSequence" -> 14L
                    "insert" -> {
                        transactionRunner.assertActive()
                        event = args.first() as com.chen.memorizewords.data.wordbook.local.room.model.learning.event.LearningEventEntity
                        Unit
                    }
                    else -> unexpected(methodName)
                }
            },
            dailyProgressProjectionTaskDao = proxy { methodName, args ->
                when (methodName) {
                    "insert" -> {
                        transactionRunner.assertActive()
                        task = args.first() as com.chen.memorizewords.data.wordbook.local.room.model.learning.projection.DailyProgressProjectionTaskEntity
                        Unit
                    }
                    else -> unexpected(methodName)
                }
            },
            wordStudyRecordDao = proxy { methodName, args ->
                when (methodName) {
                    "upsert" -> {
                        transactionRunner.assertActive()
                        record = args.first() as com.chen.memorizewords.data.wordbook.local.room.model.learning.record.WordStudyRecordEntity
                        1L
                    }
                    "getNewWordCount" -> 15
                    "getReviewWordCount" -> 3
                    else -> unexpected(methodName)
                }
            },
            wordLearningStateDao = proxy { methodName, _ ->
                when (methodName) {
                    "getState" -> null
                    "upsert" -> {
                        transactionRunner.assertActive()
                        Unit
                    }
                    else -> unexpected(methodName)
                }
            },
            wordBookProgressDao = proxy { methodName, _ ->
                when (methodName) {
                    "getProgress" -> null
                    "upsert" -> 1L
                    else -> unexpected(methodName)
                }
            },
            currentWordBookSelectionDao = currentSelectionDao(bookId = 1L),
            wordBookDao = proxy { methodName, _ ->
                when (methodName) {
                    "getWordBookById" -> wordBookEntity(1L)
                    else -> unexpected(methodName)
                }
            },
            bookWordItemDao = proxy { methodName, _ ->
                when (methodName) {
                    "existsWordInBook" -> true
                    else -> unexpected(methodName)
                }
            },
            wordDefinitionDao = proxy { methodName, _ ->
                when (methodName) {
                    "getWordDefinitions" -> emptyList<Any>()
                    else -> unexpected(methodName)
                }
            },
            gson = Gson(),
            coordinator = ImmediateBookLearningWriteCoordinator,
            learningEventSyncPort = syncPort
        )

        repository.record(
            command(bookId = 1L).copy(
                dailyNewTarget = 15,
                dailyReviewTarget = 30,
                isNewWordOverride = true
            )
        )

        val committedEvent = assertNotNull(event)
        val committedRecord = assertNotNull(record)
        val committedTask = assertNotNull(task)
        assertEquals(1, transactionRunner.transactionCount)
        assertEquals(committedEvent.clientEventId, committedTask.clientEventId)
        assertEquals(15L, committedTask.clientSequence)
        assertEquals(committedEvent.businessDate, committedTask.businessDate)
        assertEquals(committedRecord.date, committedTask.businessDate)
        assertEquals(15, committedTask.newCountAfter)
        assertEquals(3, committedTask.reviewCountAfter)
        assertEquals(15, committedTask.dailyNewTarget)
        assertEquals(30, committedTask.dailyReviewTarget)
        assertEquals(committedEvent.clientEventId, syncPort.payload?.clientEventId)
    }

    @Test
    fun `record rejects non current wordbook before writing learning facts`() = runBlocking {
        val repository = LearningCommandRepository(
            transactionRunner = FakeWordBookTransactionRunner(),
            learningEventDao = throwingProxy(),
            dailyProgressProjectionTaskDao = throwingProxy(),
            wordStudyRecordDao = throwingProxy(),
            wordLearningStateDao = throwingProxy(),
            wordBookProgressDao = throwingProxy(),
            currentWordBookSelectionDao = currentSelectionDao(bookId = 2L),
            wordBookDao = throwingProxy(),
            bookWordItemDao = throwingProxy(),
            wordDefinitionDao = throwingProxy(),
            gson = Gson(),
            coordinator = ImmediateBookLearningWriteCoordinator,
            learningEventSyncPort = throwingProxy<LearningEventSyncPort>()
        )

        val error = assertFailsWith<IllegalStateException> {
            repository.record(command(bookId = 1L))
        }

        assertTrue(error.message.orEmpty().contains("not current word book"))
    }

    private class FakeWordBookTransactionRunner : WordBookTransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    private class TrackingWordBookTransactionRunner : WordBookTransactionRunner {
        var transactionCount = 0
        private var active = false

        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            check(!active)
            transactionCount++
            active = true
            return try {
                block()
            } finally {
                active = false
            }
        }

        fun assertActive() {
            assertTrue(active, "write must happen inside the WordBook transaction")
        }
    }

    private object ImmediateBookLearningWriteCoordinator : BookLearningWriteCoordinator {
        override suspend fun <T> withBookWrite(bookId: Long, block: suspend () -> T): T = block()
    }

    private class RecordingLearningEventSyncPort : LearningEventSyncPort {
        var payload: LearningEventSyncPayload? = null
        override fun schedule(payload: LearningEventSyncPayload) {
            this.payload = payload
        }
    }

    private companion object {
        fun currentSelectionDao(bookId: Long?): CurrentWordBookSelectionDao {
            return proxy { methodName, _ ->
                when (methodName) {
                    "getById" -> bookId?.let { CurrentWordBookSelectionEntity(bookId = it) }
                    else -> unexpected(methodName)
                }
            }
        }

        fun command(bookId: Long): RecordLearningEventCommand {
            return RecordLearningEventCommand(
                bookId = bookId,
                word = Word(
                    id = 100L,
                    word = "abandon",
                    normalizedWord = "abandon",
                    phoneticUS = null,
                    phoneticUK = null,
                    hasIrregularForms = false,
                    memoryTip = null,
                    mnemonicImageUrl = null,
                    memoryAssociations = emptyList(),
                    wordFamily = null,
                    synonyms = emptyList(),
                    antonyms = emptyList(),
                    tags = emptyList(),
                    notes = null,
                    rootMemoryTip = null
                ),
                action = LearningEventAction.LEARNED,
                quality = 4,
                correct = true,
                businessDate = "2026-07-08",
                occurredAt = 1_000L
            )
        }

        fun wordBookEntity(id: Long) = WordBookEntity(
            id = id,
            title = "Book $id",
            category = "test",
            imgUrl = "",
            description = "",
            totalWords = 100,
            isNew = false,
            isHot = false,
            isPublic = true,
            createdByUserId = null
        )

        inline fun <reified T : Any> throwingProxy(): T = proxy { methodName, _ ->
            unexpected(methodName)
        }

        inline fun <reified T : Any> proxy(
            crossinline handler: (String, List<Any?>) -> Any?
        ): T {
            return Proxy.newProxyInstance(
                T::class.java.classLoader,
                arrayOf(T::class.java),
                InvocationHandler { _, method, args ->
                    handler(method.name, args?.toList().orEmpty())
                }
            ) as T
        }

        fun unexpected(methodName: String): Nothing {
            throw AssertionError("Unexpected call: $methodName")
        }
    }
}
