package com.chen.memorizewords.domain.word
data class WordRef(
    val id: Long,
    val text: String,
    val normalizedText: String = text.lowercase()
)

data class WordDefinition(
    val wordId: Long,
    val partOfSpeech: String,
    val meaning: String
)

data class WordDetail(
    val word: WordRef,
    val definitions: List<WordDefinition>,
    val phoneticUs: String? = null,
    val phoneticUk: String? = null
)

interface WordRepository {
    suspend fun wordsByIds(ids: List<Long>): List<WordDetail>
    suspend fun wordByText(text: String): WordDetail?
    suspend fun lookupAndCache(text: String): Result<WordDetail>
}
