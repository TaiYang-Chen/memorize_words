package com.chen.memorizewords.data.local.room.model.words.root.rootexample

import com.chen.memorizewords.domain.model.words.root.RootExample

fun RootExampleEntity.toDomain(): RootExample {
    return RootExample(
        id = id,
        meaningId = meaningId,
        exampleSentence = exampleSentence,
        translation = translation
    )
}

fun RootExample.toEntity(): RootExampleEntity {
    return RootExampleEntity(
        id = id,
        meaningId = meaningId,
        exampleSentence = exampleSentence,
        translation = translation
    )
}
