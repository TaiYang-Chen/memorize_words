package com.chen.memorizewords.data.wordbook.repository

import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words.BookWordItemDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordDao
import com.chen.memorizewords.domain.sync.OutboxCommand
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.sync.WordBookDeleteSyncPayload
import com.google.gson.Gson
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class WordBookRepositoryImplTest {

    @Test
    fun `delete my wordbook removes local book after remote success`() = runBlocking {
        val fixture = RepositoryFixture(
            books = listOf(wordBook(1L), wordBook(2L)),
            selectedBookId = 1L
        )

        val result = fixture.repository.deleteMyWordBook(2L)

        assertTrue(result.isSuccess)
        assertFalse(fixture.wordBookDao.books.containsKey(2L))
        assertEquals(listOf(2L), fixture.wordBookDao.deletedBookIds)
        assertTrue(fixture.remoteRemover.removedBookIds.isEmpty())
        assertEquals(listOf(2L), fixture.workCanceller.cancelledBookIds)

        val command = fixture.syncOutboxWriter.commands.single()
        assertEquals(OutboxTopic.WORD_BOOK_DELETE, command.topic)
        assertEquals("word_book_delete:2", command.key)
        assertEquals(SyncOperation.DELETE, command.operation)
        assertEquals(
            2L,
            Gson().fromJson(command.payload, WordBookDeleteSyncPayload::class.java).bookId
        )
    }

    @Test
    fun `delete my wordbook removes local book without remote call`() = runBlocking {
        val fixture = RepositoryFixture(
            books = listOf(wordBook(1L), wordBook(2L)),
            selectedBookId = 1L
        )

        val result = fixture.repository.deleteMyWordBook(2L)

        assertTrue(result.isSuccess)
        assertFalse(fixture.wordBookDao.books.containsKey(2L))
        assertEquals(listOf(2L), fixture.wordBookDao.deletedBookIds)
        assertTrue(fixture.remoteRemover.removedBookIds.isEmpty())
        assertEquals(listOf(2L), fixture.workCanceller.cancelledBookIds)
        assertEquals(listOf(OutboxTopic.WORD_BOOK_DELETE), fixture.syncOutboxWriter.commands.map { it.topic })
    }

    @Test
    fun `delete my wordbook rejects current wordbook before remote call`() = runBlocking {
        val fixture = RepositoryFixture(
            books = listOf(wordBook(1L), wordBook(2L)),
            selectedBookId = 2L
        )

        val result = fixture.repository.deleteMyWordBook(2L)

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertTrue(fixture.wordBookDao.books.containsKey(2L))
        assertTrue(fixture.remoteRemover.removedBookIds.isEmpty())
        assertTrue(fixture.wordBookDao.deletedBookIds.isEmpty())
        assertTrue(fixture.syncOutboxWriter.commands.isEmpty())
    }

    @Test
    fun `delete my wordbook flow removes deleted item`() = runBlocking {
        val fixture = RepositoryFixture(
            books = listOf(wordBook(1L), wordBook(2L)),
            selectedBookId = 1L
        )

        fixture.repository.deleteMyWordBook(2L).getOrThrow()

        val bookIds = fixture.repository.getMyWordBooksMinimalFlow().first().map { it.bookId }
        assertEquals(listOf(1L), bookIds)
    }

    private class RepositoryFixture(
        books: List<WordBookEntity>,
        selectedBookId: Long?
    ) {
        val wordBookDao = FakeWordBookDao(books)
        val currentSelectionDao = FakeCurrentWordBookSelectionDao(selectedBookId)
        val remoteRemover = FakeMyWordBookRemoteRemover()
        val workCanceller = FakeWordBookWorkCanceller()
        val syncOutboxWriter = FakeSyncOutboxWriter()

        val repository = WordBookRepositoryImpl(
            transactionRunner = FakeWordBookTransactionRunner(),
            wordLearningStateDao = throwingProxy(),
            wordBookProgressDao = throwingProxy(),
            bookWordsDao = throwingProxy(),
            wordDao = throwingProxy(),
            wordBookDao = wordBookDao.proxy,
            currentWordBookSelectionDao = currentSelectionDao.proxy,
            myWordBookRemoteRemover = remoteRemover,
            wordBookWorkCanceller = workCanceller,
            SyncOutboxWriter = syncOutboxWriter,
            gson = Gson()
        )
    }

    private class FakeWordBookDao(initialBooks: List<WordBookEntity>) {
        val books = initialBooks.associateBy { it.id }.toMutableMap()
        val deletedBookIds = mutableListOf<Long>()
        private val booksFlow = MutableStateFlow(books.values.sortedBy { it.id })

        val proxy: WordBookDao = proxy { methodName, args ->
            when (methodName) {
                "exists" -> books.containsKey(args.first() as Long)
                "deleteByIds" -> {
                    @Suppress("UNCHECKED_CAST")
                    val ids = args.first() as List<Long>
                    deletedBookIds += ids
                    ids.forEach(books::remove)
                    booksFlow.value = books.values.sortedBy { it.id }
                    Unit
                }
                "getMyWordBooksFlow", "getAllWordBooksFlow" -> booksFlow
                "getWordBookById" -> books[args.first() as Long]
                else -> unexpected(methodName)
            }
        }
    }

    private class FakeCurrentWordBookSelectionDao(selectedBookId: Long?) {
        private val selectionFlow = MutableStateFlow(
            selectedBookId?.let { CurrentWordBookSelectionEntity(bookId = it) }
        )

        val proxy: CurrentWordBookSelectionDao = proxy { methodName, args ->
            when (methodName) {
                "getById" -> selectionFlow.value
                "observeById" -> selectionFlow
                "upsert" -> {
                    selectionFlow.value = args.first() as CurrentWordBookSelectionEntity
                    Unit
                }
                "deleteAll" -> {
                    selectionFlow.value = null
                    Unit
                }
                else -> unexpected(methodName)
            }
        }
    }

    private class FakeMyWordBookRemoteRemover : MyWordBookRemoteRemover {
        val removedBookIds = mutableListOf<Long>()

        override suspend fun removeMyWordBook(bookId: Long): Result<Unit> {
            removedBookIds += bookId
            return Result.success(Unit)
        }
    }

    private class FakeWordBookTransactionRunner : WordBookTransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    private class FakeWordBookWorkCanceller : WordBookWorkCanceller {
        val cancelledBookIds = mutableListOf<Long>()

        override fun cancel(bookId: Long) {
            cancelledBookIds += bookId
        }
    }

    private class FakeSyncOutboxWriter : SyncOutboxWriter {
        val commands = mutableListOf<OutboxCommand>()

        override suspend fun enqueueLatest(command: OutboxCommand) {
            commands += command
        }
    }

    private companion object {
        fun wordBook(id: Long): WordBookEntity {
            return WordBookEntity(
                id = id,
                title = "Book $id",
                category = "category",
                imgUrl = "",
                description = "",
                totalWords = 10,
                isNew = false,
                isHot = false,
                isPublic = true,
                createdByUserId = null
            )
        }

        @Suppress("UNCHECKED_CAST")
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
