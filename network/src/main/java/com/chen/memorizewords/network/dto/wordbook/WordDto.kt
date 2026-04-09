package com.chen.memorizewords.network.dto.wordbook

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WordDto(
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
    val rootMemoryTip: String?,
    val definitionDtos: List<WordDefinitionDto>,
    val exampleDtos: List<WordExampleDto>,
    val wordFormDtos: List<WordFormDto>,
    val rootWords: List<WordRootDto>
)
