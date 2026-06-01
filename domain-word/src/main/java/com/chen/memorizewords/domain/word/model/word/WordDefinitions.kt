package com.chen.memorizewords.domain.word.model.word
import com.chen.memorizewords.domain.word.model.enums.PartOfSpeech

data class WordDefinitions(
    val id: Long,
    val wordId: Long,
    val partOfSpeech: PartOfSpeech,
    val meaningChinese: String
)