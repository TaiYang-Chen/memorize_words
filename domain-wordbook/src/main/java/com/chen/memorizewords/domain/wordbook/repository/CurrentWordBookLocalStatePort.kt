package com.chen.memorizewords.domain.wordbook.repository

import com.chen.memorizewords.domain.wordbook.model.WordBook

interface CurrentWordBookLocalStatePort {
    suspend fun getCurrentWordBookSelectionId(): Long?
    suspend fun upsertBookAndSelectionFromRemote(book: WordBook?)
    suspend fun upsertBooksAndSelectionFromRemote(books: List<WordBook>)
    suspend fun clearSelectionFromRemote()
    suspend fun clearLocalState()
}
