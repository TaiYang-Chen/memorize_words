package com.chen.memorizewords.data.wordbook.remote.wordbook

import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordDto
import com.chen.memorizewords.core.network.http.PageData

// 远端数据源接口（具体实现使用 Retrofit）
interface RemoteWordBookDataSource {
    suspend fun getWordBooks(): Result<List<WordBookDto>>
    suspend fun getBookWords(bookId: Long, page: Int, count: Int): Result<PageData<WordDto>>
    suspend fun lookupWord(word: String, normalizedWord: String): Result<WordDto?>
}
