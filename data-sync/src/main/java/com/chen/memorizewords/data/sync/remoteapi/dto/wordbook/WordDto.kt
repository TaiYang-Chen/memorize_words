package com.chen.memorizewords.data.sync.remoteapi.dto.wordbook

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WordDto(
    val id: Long,
    val term: String,
    val normalized: String,
    val pronunciation: WordPronunciationDto? = null,
    val definitions: List<WordDefinitionDto> = emptyList(),
    val examples: List<WordExampleDto> = emptyList(),
    val forms: List<WordFormDto> = emptyList(),
    val relations: WordRelationsDto = WordRelationsDto(),
    val roots: List<WordRootDto> = emptyList(),
    val memory: WordMemoryDto? = null
) {
    val word: String get() = term
    val normalizedWord: String get() = normalized
    val phoneticUS: String? get() = pronunciation?.us
    val phoneticUK: String? get() = pronunciation?.uk
    val hasIrregularForms: Boolean get() = false
    val memoryTip: String? get() = memory?.hint
    val mnemonicImageUrl: String? get() = memory?.imageUrl
    val memoryAssociations: List<String> get() = relations.associations
    val wordFamily: String? get() = null
    val synonyms: List<String> get() = relations.synonyms
    val antonyms: List<String> get() = relations.antonyms
    val tags: List<String> get() = relations.tags
    val notes: String? get() = memory?.notes
    val rootMemoryTip: String? get() = memory?.rootHint
    val definitionDtos: List<WordDefinitionDto> get() = definitions.map { it.copy(wordId = id) }
    val exampleDtos: List<WordExampleDto> get() = examples.map { it.copy(wordId = id) }
    val wordFormDtos: List<WordFormDto> get() = forms.map { it.copy(wordId = id) }
    val rootWords: List<WordRootDto> get() = roots
}

@JsonClass(generateAdapter = false)
data class WordPronunciationDto(
    val us: String? = null,
    val uk: String? = null
)

@JsonClass(generateAdapter = false)
data class WordRelationsDto(
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val associations: List<String> = emptyList()
)

@JsonClass(generateAdapter = false)
data class WordMemoryDto(
    val hint: String? = null,
    val rootHint: String? = null,
    val imageUrl: String? = null,
    val notes: String? = null
)
