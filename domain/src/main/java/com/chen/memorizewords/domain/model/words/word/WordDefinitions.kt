package com.chen.memorizewords.domain.model.words.word

import com.chen.memorizewords.domain.model.words.enums.PartOfSpeech

data class WordDefinitions(
    val id: Long,
    val wordId: Long,
    val partOfSpeech: PartOfSpeech,
    val meaningChinese: String
)