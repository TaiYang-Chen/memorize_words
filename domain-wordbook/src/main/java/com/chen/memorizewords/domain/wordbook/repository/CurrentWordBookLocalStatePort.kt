package com.chen.memorizewords.domain.wordbook.repository

interface CurrentWordBookLocalStatePort {
    suspend fun overwriteFromRemote(bookId: Long?)
    suspend fun clearLocalState()
}
