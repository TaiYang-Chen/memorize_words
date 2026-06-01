package com.chen.memorizewords.data.wordbook.repository

import androidx.room.withTransaction
import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import javax.inject.Inject

interface WordBookTransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

class RoomWordBookTransactionRunner @Inject constructor(
    private val database: WordBookDatabase
) : WordBookTransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return database.withTransaction { block() }
    }
}
