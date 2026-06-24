package com.chen.memorizewords.domain.wordbook.repository

import com.chen.memorizewords.domain.wordbook.model.WordBook

interface WordBookContentReadinessPort {
    suspend fun ensureContentReady(
        book: WordBook,
        forceRefresh: Boolean = false
    )
}
