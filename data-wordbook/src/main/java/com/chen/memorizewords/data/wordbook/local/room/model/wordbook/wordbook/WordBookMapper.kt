package com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook

import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordBookDto

fun WordBookEntity.toDomain(isSelected: Boolean = false) = WordBook(
    id = id,
    title = title,
    category = category,
    imgUrl = imgUrl,
    description = description,
    totalWords = totalWords,
    contentVersion = contentVersion,
    isNew = isNew,
    isHot = isHot,
    isSelected = isSelected,
    isPublic = isPublic,
    createdByUserId = createdByUserId
)

fun WordBook.toEntity() = WordBookEntity(
    id = id,
    title = title,
    category = category,
    imgUrl = imgUrl,
    description = description,
    totalWords = totalWords,
    contentVersion = contentVersion,
    isNew = isNew,
    isHot = isHot,
    isPublic = isPublic,
    createdByUserId = createdByUserId
)

fun WordBookDto.toEntity(): WordBookEntity {
    return WordBookEntity(
        id = id,
        title = title,
        category = category,
        imgUrl = imgUrl,
        description = description,
        totalWords = totalWords,
        contentVersion = contentVersion,
        isNew = isNew,
        isHot = isHot,
        isPublic = isPublic,
        createdByUserId = createdByUserId
    )
}
