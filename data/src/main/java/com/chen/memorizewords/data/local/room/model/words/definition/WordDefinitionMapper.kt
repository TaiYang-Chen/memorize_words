package com.chen.memorizewords.data.local.room.model.words.definition

import com.chen.memorizewords.domain.model.words.enums.PartOfSpeech
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.network.dto.wordbook.WordDefinitionDto


fun WordDefinitionDto.toEntity() = WordDefinitionEntity(
    id = id,
    wordId = wordId,
    partOfSpeech = PartOfSpeech.fromString(partOfSpeech),
    meaningChinese = definition
)

fun WordDefinitions.toEntity() = WordDefinitionEntity(
    id = id,
    wordId = wordId,
    partOfSpeech = partOfSpeech,
    meaningChinese = meaningChinese
)

fun WordDefinitionEntity.toDomain() = WordDefinitions(
    id = id,
    wordId = wordId,
    partOfSpeech = partOfSpeech,
    meaningChinese = meaningChinese
)
