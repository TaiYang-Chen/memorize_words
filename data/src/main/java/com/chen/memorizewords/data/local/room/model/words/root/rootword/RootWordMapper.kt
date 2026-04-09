package com.chen.memorizewords.data.local.room.model.words.root.rootword

import com.chen.memorizewords.domain.model.words.root.RootWord
import com.chen.memorizewords.network.dto.wordbook.WordRootDto

/**
 * 将数据层实体 [RootWordEntity] 转换为领域层模型 [RootWord]。
 */
fun RootWordEntity.toDomain(): RootWord {
    return RootWord(
        wordId = this.wordId,
        rootId = this.rootId,
        context = this.context,
        partOfSpeech = this.partOfSpeech,
        sequence = this.sequence
    )
}

/**
 * 将领域层模型 [RootWord] 转换为数据层实体 [RootWordEntity]。
 */
fun RootWord.toEntity(): RootWordEntity {
    return RootWordEntity(
        wordId = this.wordId,
        rootId = this.rootId,
        context = this.context,
        partOfSpeech = this.partOfSpeech,
        sequence = this.sequence
    )
}

fun WordRootDto.toRelationEntity(wordId: Long, sequence: Int): RootWordEntity {
    return RootWordEntity(
        wordId = wordId,
        rootId = this.id,
        context = this.rootWord,
        partOfSpeech = "",
        sequence = sequence
    )
}
