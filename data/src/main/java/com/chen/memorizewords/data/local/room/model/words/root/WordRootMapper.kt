package com.chen.memorizewords.data.local.room.model.words.root.root

import com.chen.memorizewords.domain.model.words.word.WordRoot
import com.chen.memorizewords.network.dto.wordbook.WordRootDto

fun WordRootEntity.toDomain(): WordRoot {
    return WordRoot(
        id = id,
        rootWord = rootWord,
        coreMeaning = coreMeaning,
        etymology = etymology,
        sourceLanguage = sourceLanguage,
        difficulty = difficulty,
        tags = tags,
        meanings = emptyList(),
        variants = emptyList()
    )
}

fun WordRootDto.toEntity(): WordRootEntity {
    return WordRootEntity(
        id = id,
        rootWord = rootWord,
        coreMeaning = coreMeaning,
        etymology = etymology,
        sourceLanguage = sourceLanguage,
        difficulty = difficulty,
        tags = tags
    )
}

fun WordRoot.toEntity(): WordRootEntity {
    return WordRootEntity(
        id = id,
        rootWord = rootWord,
        coreMeaning = coreMeaning,
        etymology = etymology,
        sourceLanguage = sourceLanguage,
        difficulty = difficulty,
        tags = tags
    )
}
