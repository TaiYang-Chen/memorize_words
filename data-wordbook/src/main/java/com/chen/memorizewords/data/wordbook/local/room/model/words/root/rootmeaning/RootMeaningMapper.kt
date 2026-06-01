package com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootmeaning

import com.chen.memorizewords.domain.word.model.root.RootMeaning

fun RootMeaningEntity.toDomain(): RootMeaning {
    return RootMeaning(
        id = id,
        rootId = rootId,
        meaning = meaning,
        examples = emptyList()
    )
}

fun RootMeaning.toEntity(): RootMeaningEntity {
    return RootMeaningEntity(
        id = id,
        rootId = rootId,
        meaning = meaning
    )
}
