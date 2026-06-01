package com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootvariant

import com.chen.memorizewords.domain.word.model.root.RootVariant

fun RootVariantEntity.toDomain(): RootVariant {
    return RootVariant(
        id = id,
        rootId = rootId,
        variant = variant
    )
}

fun RootVariant.toEntity(): RootVariantEntity {
    return RootVariantEntity(
        id = id,
        rootId = rootId,
        variant = variant
    )
}
