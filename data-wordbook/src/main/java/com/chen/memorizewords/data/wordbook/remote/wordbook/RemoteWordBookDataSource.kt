package com.chen.memorizewords.data.wordbook.remote.wordbook

import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordDto
import com.chen.memorizewords.core.network.http.PageData

// 杩滅鏁版嵁婧愭帴鍙ｏ紙鍏蜂綋瀹炵幇浣跨敤 Retrofit锟?
interface RemoteWordBookDataSource {
    suspend fun getWordBooks(): Result<List<WordBookDto>>
    suspend fun getBookWords(bookId: Long, page: Int, count: Int): Result<PageData<WordDto>>
    suspend fun lookupWord(word: String, normalizedWord: String): Result<WordDto?>
}
