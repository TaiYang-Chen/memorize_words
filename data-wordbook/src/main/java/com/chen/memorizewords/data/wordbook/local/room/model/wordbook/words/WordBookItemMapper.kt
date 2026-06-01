package com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words

import com.chen.memorizewords.domain.wordbook.model.WordBookItem

fun WordBookItemEntity.toDomain() = WordBookItem(
    wordBookId = wordBookId,
    wordId = wordId
)

fun WordBookItem.toEntity() = WordBookItemEntity(
    wordBookId = wordBookId,
    wordId = wordId
)
