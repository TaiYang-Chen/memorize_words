package com.chen.memorizewords.domain.model.words.word

data class Word(
    val id: Long,
    val word: String,
    val normalizedWord: String,
    val phoneticUS: String?,
    val phoneticUK: String?,
    val hasIrregularForms: Boolean,
    val memoryTip: String?,
    val mnemonicImageUrl: String?,
    val memoryAssociations: List<String>,
    val wordFamily: String?,
    val synonyms: List<String>,
    val antonyms: List<String>,
    val tags: List<String>,
    val notes: String?,
    val rootMemoryTip: String?
)
