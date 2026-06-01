package com.chen.memorizewords.data.wordbook.local.room.model.words.root.root

import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.RootTagEntity
import com.chen.memorizewords.domain.word.model.word.WordRoot
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordRootDto

fun WordRootEntity.toDomain(tags: List<RootTagEntity> = emptyList()): WordRoot {
    return WordRoot(
        id = id,
        rootWord = rootWord,
        coreMeaning = coreMeaning,
        etymology = etymology,
        sourceLanguage = sourceLanguage,
        difficulty = difficulty,
        tags = tags.map { it.value },
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
        difficulty = difficulty
    )
}

fun WordRoot.toEntity(): WordRootEntity {
    return WordRootEntity(
        id = id,
        rootWord = rootWord,
        coreMeaning = coreMeaning,
        etymology = etymology,
        sourceLanguage = sourceLanguage,
        difficulty = difficulty
    )
}

fun WordRootDto.toTagEntities(): List<RootTagEntity> {
    return tags.toRootTagValues().map { value ->
        RootTagEntity(
            rootId = id,
            value = value,
            normalizedValue = value.lowercase()
        )
    }
}

fun WordRoot.toTagEntities(): List<RootTagEntity> {
    return tags.toRootTagValues().map { value ->
        RootTagEntity(
            rootId = id,
            value = value,
            normalizedValue = value.lowercase()
        )
    }
}

private fun String?.toRootTagValues(): List<String> {
    return this
        ?.split(',', '\uff0c', ';', '\uff1b', '|', '\u3001')
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.distinctBy { it.lowercase() }
        ?: emptyList()
}

private fun List<String>.toRootTagValues(): List<String> {
    return map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
}
