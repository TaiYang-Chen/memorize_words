package com.chen.memorizewords.domain.model.words.word

import com.chen.memorizewords.domain.model.words.word.WordRoot

data class WordQuickLookupResult(
    val status: Status,
    val queryRawWord: String,
    val normalizedWord: String,
    val word: Word? = null,
    val definitions: List<WordDefinitions> = emptyList(),
    val examples: List<WordExample> = emptyList(),
    val forms: List<WordForm> = emptyList(),
    val roots: List<WordRoot> = emptyList(),
    val fromNetwork: Boolean = false,
    val errorMessage: String? = null
) {
    enum class Status {
        FOUND,
        MISSING,
        ERROR
    }
}
