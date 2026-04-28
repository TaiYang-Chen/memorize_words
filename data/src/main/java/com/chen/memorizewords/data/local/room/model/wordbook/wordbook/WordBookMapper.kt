package com.chen.memorizewords.data.local.room.model.wordbook.wordbook

import com.chen.memorizewords.domain.model.wordbook.WordBook
import com.chen.memorizewords.network.dto.wordbook.WordBookDto

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
