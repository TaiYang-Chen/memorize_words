package com.chen.memorizewords.data.local.room.model.wordbook.words

import com.chen.memorizewords.domain.model.wordbook.WordBookItem

fun WordBookItemEntity.toDomain() = WordBookItem(
    wordBookId = wordBookId,
    wordId = wordId
)

fun WordBookItem.toEntity() = WordBookItemEntity(
    wordBookId = wordBookId,
    wordId = wordId
)
