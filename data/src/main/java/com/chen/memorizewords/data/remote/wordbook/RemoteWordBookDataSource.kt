package com.chen.memorizewords.data.remote.wordbook

import com.chen.memorizewords.network.dto.wordbook.WordBookDto
import com.chen.memorizewords.network.dto.wordbook.WordDto
import com.chen.memorizewords.network.model.PageData

// 远端数据源接口（具体实现使用 Retrofit）
interface RemoteWordBookDataSource {
    suspend fun getWordBooks(): Result<List<WordBookDto>>
    suspend fun getBookWords(bookId: Long, page: Int, count: Int): Result<PageData<WordDto>>
    suspend fun lookupWord(word: String, normalizedWord: String): Result<WordDto?>
}
