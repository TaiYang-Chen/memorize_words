package com.chen.memorizewords.domain.wordbook.repository

import com.chen.memorizewords.domain.wordbook.model.WordBook

interface CurrentWordBookLocalStatePort {
    suspend fun upsertBookAndSelectionFromRemote(book: WordBook?)
    suspend fun overwriteFromRemote(bookId: Long?)
    suspend fun clearLocalState()
}
