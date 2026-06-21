package com.chen.memorizewords.data.wordbook.repository.wordbook

import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.repository.WordBookContentReadinessPort
import javax.inject.Inject

class WordBookContentReadinessAdapter @Inject constructor(
    private val downloader: WordBookContentDownloader
) : WordBookContentReadinessPort {
    override suspend fun ensureContentReady(
        book: WordBook,
        forceRefresh: Boolean
    ) {
        downloader.ensureContentReady(
            book = book,
            forceRefresh = forceRefresh
        )
    }
}
