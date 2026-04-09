package com.chen.memorizewords.data.local.room.model.words.example

import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.network.dto.wordbook.WordExampleDto

fun WordExampleEntity.toDomain(): WordExample {
    return WordExample(
        id = id,
        wordId = wordId,
        definitionId = definitionId,
        englishSentence = englishSentence,
        chineseTranslation = chineseTranslation,
        difficultyLevel = WordExample.DifficultyLevel.valueOf(difficultyLevel.name)
    )
}

fun WordExampleDto.toEntity(): WordExampleEntity {
    return WordExampleEntity(
        id = id,
        wordId = wordId,
        definitionId = definitionId,
        englishSentence = englishSentence,
        chineseTranslation = chineseTranslation,
        difficultyLevel = WordExampleEntity.DifficultyLevel.fromInt(difficultyLevel)
    )
}

fun WordExample.toEntity(): WordExampleEntity {
    return WordExampleEntity(
        id = id,
        wordId = wordId,
        definitionId = definitionId,
        englishSentence = englishSentence,
        chineseTranslation = chineseTranslation,
        difficultyLevel = WordExampleEntity.DifficultyLevel.valueOf(difficultyLevel.name)
    )
}
