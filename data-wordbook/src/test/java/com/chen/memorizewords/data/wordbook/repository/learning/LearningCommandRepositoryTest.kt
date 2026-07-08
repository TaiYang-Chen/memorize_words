package com.chen.memorizewords.data.wordbook.repository.learning

import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionEntity
import com.chen.memorizewords.data.wordbook.repository.WordBookTransactionRunner
import com.chen.memorizewords.domain.study.model.learning.LearningEventAction
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventCommand
import com.chen.memorizewords.domain.word.model.word.Word
import com.google.gson.Gson
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LearningCommandRepositoryTest {

    @Test
    fun `record rejects non current wordbook before writing learning facts`() = runBlocking {
        val repository = LearningCommandRepository(
            transactionRunner = FakeWordBookTransactionRunner(),
            learningEventDao = throwingProxy(),
            learningOutboxDao = throwingProxy(),
            wordStudyRecordDao = throwingProxy(),
            wordLearningStateDao = throwingProxy(),
            wordBookProgressDao = throwingProxy(),
            currentWordBookSelectionDao = currentSelectionDao(bookId = 2L),
            wordBookDao = throwingProxy(),
            bookWordItemDao = throwingProxy(),
            wordDefinitionDao = throwingProxy(),
            gson = Gson()
        )

        val error = assertFailsWith<IllegalStateException> {
            repository.record(command(bookId = 1L))
        }

        assertTrue(error.message.orEmpty().contains("not current word book"))
    }

    private class FakeWordBookTransactionRunner : WordBookTransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
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
