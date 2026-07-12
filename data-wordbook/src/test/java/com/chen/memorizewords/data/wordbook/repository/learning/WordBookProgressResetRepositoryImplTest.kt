package com.chen.memorizewords.data.wordbook.repository.learning

import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionEntity
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.wordbook.repository.WordBookTransactionRunner
import com.chen.memorizewords.domain.study.repository.learning.BookLearningWriteCoordinator
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordBookProgressResetRepositoryImplTest {

    @Test
    fun `server success deletes only target state and progress locally`() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = repository(remoteFails = false, calls = calls)

        repository.resetCurrentWordBookProgress(1001L).getOrThrow()

        assertEquals(
            listOf(
                "lock:1001",
                "selection",
                "remote:1001",
                "transaction",
                "states:1001",
                "progress:1001"
            ),
            calls
        )
    }

    @Test
    fun `server failure leaves local progress unchanged`() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = repository(remoteFails = true, calls = calls)

        assertTrue(repository.resetCurrentWordBookProgress(1001L).isFailure)
        assertEquals(listOf("lock:1001", "selection", "remote:1001"), calls)
    }

    private fun repository(
        remoteFails: Boolean,
        calls: MutableList<String>
    ): WordBookProgressResetRepositoryImpl {
        return WordBookProgressResetRepositoryImpl(
            coordinator = object : BookLearningWriteCoordinator {
                override suspend fun <T> withBookWrite(bookId: Long, block: suspend () -> T): T {
                    calls += "lock:$bookId"
                    return block()
                }
            },
            transactionRunner = object : WordBookTransactionRunner {
                override suspend fun <T> runInTransaction(block: suspend () -> T): T {
                    calls += "transaction"
                    return block()
                }
            },
            currentWordBookSelectionDao = proxy<CurrentWordBookSelectionDao> { name, _ ->
                if (name == "getById") {
                    calls += "selection"
                    CurrentWordBookSelectionEntity(bookId = 1001L)
                } else {
                    unexpected(name)
                }
            },
            wordLearningStateDao = proxy<WordLearningStateDao> { name, args ->
                if (name == "deleteLearningWordByBookId") {
                    calls += "states:${args.first()}"
                    Unit
                } else {
                    unexpected(name)
                }
            },
            wordBookProgressDao = proxy<WordBookProgressDao> { name, args ->
                if (name == "deleteByBookId") {
                    calls += "progress:${args.first()}"
                    Unit
                } else {
                    unexpected(name)
                }
            },
            remoteUserSyncDataSource = proxy<RemoteUserSyncDataSource> { name, args ->
                if (name.startsWith("resetWordBookProgress")) {
                    calls += "remote:${args.first()}"
                    if (remoteFails) error("server failed")
                    Unit
                } else {
                    unexpected(name)
                }
            }
        )
    }

    private companion object {
        inline fun <reified T> proxy(crossinline handler: (String, List<Any?>) -> Any?): T {
            return Proxy.newProxyInstance(
                T::class.java.classLoader,
                arrayOf(T::class.java)
            ) { _, method, rawArgs ->
                handler(method.name, rawArgs?.dropLastWhile { it is kotlin.coroutines.Continuation<*> }.orEmpty())
            } as T
        }

        fun unexpected(name: String): Nothing = error("Unexpected call: $name")
    }
}
