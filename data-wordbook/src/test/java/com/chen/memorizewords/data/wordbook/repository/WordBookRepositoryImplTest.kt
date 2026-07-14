package com.chen.memorizewords.data.wordbook.repository

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import com.chen.memorizewords.data.wordbook.local.room.model.learning.event.LearningEventDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.record.WordStudyRecordDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.syncstate.WordBookSyncStateDao
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.wordbook.remote.wordbook.RemoteWordBookDataSource
import com.chen.memorizewords.data.wordbook.repository.wordbook.WordBookContentLocalStore
import com.chen.memorizewords.data.wordbook.repository.wordbook.WordBookContentDownloader
import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.data.wordbook.local.room.wordbook.WordBookSyncStateStore
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.repository.word.FavoritesRepository
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.contentstate.WordBookContentStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words.BookWordItemDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.definition.WordDefinitionDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.example.WordExampleDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.form.WordFormDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.meta.WordUserMetaDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.relation.WordRelationDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.RootTagDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.WordRootDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootword.RootWordDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordDao
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll

class WordBookRepositoryImplTest {

    @Test
    fun `delete my wordbook removes local book then starts direct upload`() = runBlocking {
        val fixture = RepositoryFixture(
            books = listOf(wordBook(1L), wordBook(2L)),
            selectedBookId = 1L
        )

        val result = fixture.repository.deleteMyWordBook(2L)
        fixture.awaitUploads()

        assertTrue(result.isSuccess)
        assertFalse(fixture.wordBookDao.books.containsKey(2L))
        assertEquals(listOf(2L), fixture.wordBookDao.deletedBookIds)
        assertEquals(listOf(2L), fixture.removedBookIds)
        assertEquals(listOf(2L), fixture.workCanceller.cancelledBookIds)
    }

    @Test
    fun `delete my wordbook starts upload and cancels local work`() = runBlocking {
        val fixture = RepositoryFixture(
            books = listOf(wordBook(1L), wordBook(2L)),
            selectedBookId = 1L
        )

        val result = fixture.repository.deleteMyWordBook(2L)
        fixture.awaitUploads()

        assertTrue(result.isSuccess)
        assertFalse(fixture.wordBookDao.books.containsKey(2L))
        assertEquals(listOf(2L), fixture.wordBookDao.deletedBookIds)
        assertEquals(listOf(2L), fixture.removedBookIds)
        assertEquals(listOf(2L), fixture.workCanceller.cancelledBookIds)
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
        assertTrue(fixture.removedBookIds.isEmpty())
        assertTrue(fixture.wordBookDao.deletedBookIds.isEmpty())
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

    @Test
    fun `unlearned word ids use lightweight random query without exclude ids`() = runBlocking {
        val bookWordsDao = FakeBookWordItemDao(
            responses = mapOf("getRandomUnlearnedWordIdsForBook" to listOf(4L, 2L))
        )
        val fixture = RepositoryFixture(
            books = listOf(wordBook(1L)),
            selectedBookId = 1L,
            bookWordsDao = bookWordsDao.proxy
        )

        val ids = fixture.repository.getUnlearnedWordIdsForBook(
            bookId = 1L,
            count = 2,
            orderType = WordOrderType.RANDOM
        )

        assertEquals(listOf(4L, 2L), ids)
        assertEquals(listOf("getRandomUnlearnedWordIdsForBook"), bookWordsDao.calls.map { it.name })
        assertEquals(2, bookWordsDao.calls.single().limit)
    }

    @Test
    fun `unlearned word ids use lightweight ordered excluding query`() = runBlocking {
        val bookWordsDao = FakeBookWordItemDao(
            responses = mapOf("getUnlearnedWordIdsLengthDescExcluding" to listOf(9L, 7L))
        )
        val fixture = RepositoryFixture(
            books = listOf(wordBook(1L)),
            selectedBookId = 1L,
            bookWordsDao = bookWordsDao.proxy
        )

        val ids = fixture.repository.getUnlearnedWordIdsForBook(
            bookId = 1L,
            count = 3,
            orderType = WordOrderType.LENGTH_DESC,
            excludeIds = setOf(5L, 6L)
        )

        assertEquals(listOf(9L, 7L), ids)
        assertEquals(listOf("getUnlearnedWordIdsLengthDescExcluding"), bookWordsDao.calls.map { it.name })
        assertEquals(3, bookWordsDao.calls.single().limit)
        assertEquals(setOf(5L, 6L), bookWordsDao.calls.single().excludeIds.toSet())
    }

    @Test
    fun `unlearned word ids return empty for non positive count before querying dao`() = runBlocking {
        val bookWordsDao = FakeBookWordItemDao()
        val fixture = RepositoryFixture(
            books = listOf(wordBook(1L)),
            selectedBookId = 1L,
            bookWordsDao = bookWordsDao.proxy
        )

        val ids = fixture.repository.getUnlearnedWordIdsForBook(
            bookId = 1L,
            count = 0,
            orderType = WordOrderType.ALPHABETIC_ASC
        )

        assertEquals(emptyList(), ids)
        assertTrue(bookWordsDao.calls.isEmpty())
    }

    private class RepositoryFixture(
        books: List<WordBookEntity>,
        selectedBookId: Long?,
        bookWordsDao: BookWordItemDao = throwingProxy()
    ) {
        val wordBookDao = FakeWordBookDao(books)
        val currentSelectionDao = FakeCurrentWordBookSelectionDao(selectedBookId)
        val favoritesRepository = FakeFavoritesRepository()
        val removedBookIds = mutableListOf<Long>()
        val remoteUserSyncDataSource: RemoteUserSyncDataSource = proxy { methodName, args ->
            when {
                methodName.startsWith("removeMyWordBook") -> {
                    removedBookIds += args.first() as Long
                    Result.success(Unit)
                }
                else -> unexpected(methodName)
            }
        }
        val wordBookContentDownloader = throwingWordBookContentDownloader()
        val workCanceller = FakeWordBookWorkCanceller()
        private val applicationJob = SupervisorJob()

        val repository = WordBookRepositoryImpl(
            transactionRunner = FakeWordBookTransactionRunner(),
            bookWordsDao = bookWordsDao,
            wordDao = throwingProxy(),
            wordBookDao = wordBookDao.proxy,
            currentWordBookSelectionDao = currentSelectionDao.proxy,
            wordBookContentStateDao = throwingProxy(),
            favoritesRepository = favoritesRepository,
            remoteUserSyncDataSource = remoteUserSyncDataSource,
            wordBookContentDownloader = wordBookContentDownloader,
            wordBookWorkCanceller = workCanceller,
            directSyncLauncher = DirectSyncLauncher(
                CoroutineScope(applicationJob + Dispatchers.Unconfined)
            )
        )

        suspend fun awaitUploads() {
            applicationJob.children.toList().joinAll()
        }
    }

    private data class BookWordDaoCall(
        val name: String,
        val limit: Int,
        val excludeIds: List<Long>
    )

    private class FakeBookWordItemDao(
        private val responses: Map<String, List<Long>> = emptyMap()
    ) {
        val calls = mutableListOf<BookWordDaoCall>()

        val proxy: BookWordItemDao = proxy { methodName, args ->
            when (methodName) {
                "getRandomUnlearnedWordIdsForBook",
                "getUnlearnedWordIdsAlphabeticAsc",
                "getUnlearnedWordIdsAlphabeticDesc",
                "getUnlearnedWordIdsLengthAsc",
                "getUnlearnedWordIdsLengthDesc" -> {
                    calls += BookWordDaoCall(
                        name = methodName,
                        limit = args[1] as Int,
                        excludeIds = emptyList()
                    )
                    responses[methodName].orEmpty()
                }

                "getRandomUnlearnedWordIdsForBookExcluding",
                "getUnlearnedWordIdsAlphabeticAscExcluding",
                "getUnlearnedWordIdsAlphabeticDescExcluding",
                "getUnlearnedWordIdsLengthAscExcluding",
                "getUnlearnedWordIdsLengthDescExcluding" -> {
                    @Suppress("UNCHECKED_CAST")
                    val excluded = args[2] as List<Long>
                    calls += BookWordDaoCall(
                        name = methodName,
                        limit = args[1] as Int,
                        excludeIds = excluded
                    )
                    responses[methodName].orEmpty()
                }

                else -> unexpected(methodName)
            }
        }
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

    private class FakeFavoritesRepository : FavoritesRepository {
        override suspend fun addFavorite(favorites: WordFavorites) = Unit

        override suspend fun removeFavorite(wordId: Long) = Unit

        override suspend fun isFavorite(wordId: Long): Boolean = false

        override suspend fun getAllFavoriteWordIds(): List<Long> = emptyList()

        override suspend fun getFavoritesPage(
            pageIndex: Int,
            pageSize: Int
        ): PageSlice<WordFavorites> = PageSlice(emptyList(), hasNext = false)
    }

    private class ThrowingWordBookDatabase : WordBookDatabase() {
        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper = throwingProxy()

        override fun createInvalidationTracker(): InvalidationTracker = InvalidationTracker(this, "unused")

        override fun wordBookDao(): WordBookDao = throwingProxy()

        override fun currentWordBookSelectionDao(): CurrentWordBookSelectionDao = throwingProxy()

        override fun wordBookContentStateDao(): WordBookContentStateDao = throwingProxy()

        override fun wordBookSyncStateDao(): WordBookSyncStateDao = throwingProxy()

        override fun wordBookItemDao(): BookWordItemDao = throwingProxy()

        override fun wordBookProgressDao(): WordBookProgressDao = throwingProxy()

        override fun wordLearningStateDao(): WordLearningStateDao = throwingProxy()

        override fun learningEventDao(): LearningEventDao = throwingProxy()

        override fun learningOutboxDao(): LearningOutboxDao = throwingProxy()

        override fun wordStudyRecordDao(): WordStudyRecordDao = throwingProxy()

        override fun wordDao(): WordDao = throwingProxy()

        override fun wordDefinitionDao(): WordDefinitionDao = throwingProxy()

        override fun wordExampleDao(): WordExampleDao = throwingProxy()

        override fun wordFormDao(): WordFormDao = throwingProxy()

        override fun wordUserMetaDao(): WordUserMetaDao = throwingProxy()

        override fun wordRelationDao(): WordRelationDao = throwingProxy()

        override fun wordRootDao(): WordRootDao = throwingProxy()

        override fun rootTagDao(): RootTagDao = throwingProxy()

        override fun rootWordDao(): RootWordDao = throwingProxy()

        override fun clearAllTables() {
            unexpected("clearAllTables")
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

        fun throwingWordBookContentDownloader(): WordBookContentDownloader {
            val database = ThrowingWordBookDatabase()
            return WordBookContentDownloader(
                database = database,
                packageImporter = throwingProxy(),
                contentStateDao = throwingProxy(),
                syncStateStore = WordBookSyncStateStore(throwingProxy())
            )
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
