package com.chen.memorizewords.data.word.local.room.model.words.root.rootword

import com.chen.memorizewords.domain.word.model.root.RootWord
import com.chen.memorizewords.data.word.remoteapi.dto.wordbook.WordRootDto

/**
 * 灏嗘暟鎹眰瀹炰綋 [RootWordEntity] 杞崲涓洪鍩熷眰妯″瀷 [RootWord]锟?
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
 * 灏嗛鍩熷眰妯″瀷 [RootWord] 杞崲涓烘暟鎹眰瀹炰綋 [RootWordEntity]锟?
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
