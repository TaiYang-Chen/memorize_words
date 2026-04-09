package com.chen.memorizewords.data.local.room.model.words.root.rootvariant

import com.chen.memorizewords.domain.model.words.root.RootVariant

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
