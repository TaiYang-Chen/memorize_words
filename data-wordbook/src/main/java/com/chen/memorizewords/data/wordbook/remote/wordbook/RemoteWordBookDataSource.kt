package com.chen.memorizewords.data.wordbook.remote.wordbook

import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordDto

// 远端数据源接口（具体实现使用 Retrofit）
interface RemoteWordBookDataSource {
    suspend fun getWordBooks(): Result<List<WordBookDto>>
    suspend fun lookupWord(word: String, normalizedWord: String): Result<WordDto?>
}
