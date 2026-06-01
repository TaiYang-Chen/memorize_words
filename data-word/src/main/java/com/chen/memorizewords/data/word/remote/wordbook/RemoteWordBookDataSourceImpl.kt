package com.chen.memorizewords.data.word.remote.wordbook

import com.chen.memorizewords.core.network.remote.RemoteResultAdapter
import com.chen.memorizewords.data.word.remoteapi.api.wordbook.WordBookRequest
import com.chen.memorizewords.data.word.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.data.word.remoteapi.dto.wordbook.WordDto
import com.chen.memorizewords.core.network.http.PageData
import javax.inject.Inject

class RemoteWordBookDataSourceImpl @Inject constructor(
    private val wordBookRequest: WordBookRequest,
    private val remoteResultAdapter: RemoteResultAdapter
) : RemoteWordBookDataSource {

    override suspend fun getWordBooks(): Result<List<WordBookDto>> {
        return remoteResultAdapter.toResult { wordBookRequest.getWordBooks() }
    }

    override suspend fun getBookWords(
        bookId: Long,
        page: Int,
        count: Int
    ): Result<PageData<WordDto>> {
        return remoteResultAdapter.toResult { wordBookRequest.getWordBookWords(bookId, page, count) }
    }

    override suspend fun lookupWord(word: String, normalizedWord: String): Result<WordDto?> {
        return remoteResultAdapter.toResult { wordBookRequest.lookupWord(word, normalizedWord) }
    }
}
