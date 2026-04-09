package com.chen.memorizewords.domain.repository.word

import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.domain.model.words.word.WordForm
import com.chen.memorizewords.domain.model.words.word.WordQuickLookupResult
import com.chen.memorizewords.domain.model.words.word.WordRoot

/**
 * domain 层：WordRepository（单一职责）
 * 负责提供 Word 实体的读取方法（可以按 id 列表批量查询）。
 */

interface WordRepository { // 只负责单词数据
    /**
     * 根据一组 id 批量获取 Word 列表（维持入参顺序尽量一致）
     */
    suspend fun getWordsByIds(ids: List<Long>): List<Word>
    suspend fun getWordById(wordId: Long): Word?

    suspend fun getWordForms(wordId: Long): List<WordForm>
    suspend fun getRootWordByWordId(wordId: Long): List<WordRoot>
    suspend fun getWordExamples(wordId: Long): List<WordExample>
    suspend fun getWordDefinitions(wordId: Long): kotlin.collections.List<com.chen.memorizewords.domain.model.words.word.WordDefinitions>
    suspend fun getRandomDefinition(wordId: Long): WordDefinitions

    suspend fun getRandomDefinitionsByPos(
        wordId: Long,
        limit: Int
    ): List<WordDefinitions>

    suspend fun updateWordStatus(
        bookId: Long,
        word: Word,
        quality: Int
    ): Boolean

    suspend fun setWordAsMastered(
        bookId: Long,
        word: Word
    )

    suspend fun getWordByWordString(word: String): Word?
    suspend fun lookupWordQuick(normalizedWord: String, rawWord: String): WordQuickLookupResult
}
