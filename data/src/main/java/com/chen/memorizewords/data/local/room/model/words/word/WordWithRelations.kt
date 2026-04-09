package com.chen.memorizewords.data.local.room.model.words.word

import androidx.room.Embedded
import androidx.room.Relation
import com.chen.memorizewords.data.local.room.model.words.meta.WordUserMetaEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordAntonymEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordAssociationEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordSynonymEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordTagEntity

data class WordWithRelations(
    @Embedded
    val word: WordEntity,

    @Relation(parentColumn = "id", entityColumn = "word_id")
    val synonyms: List<WordSynonymEntity>,

    @Relation(parentColumn = "id", entityColumn = "word_id")
    val antonyms: List<WordAntonymEntity>,

    @Relation(parentColumn = "id", entityColumn = "word_id")
    val tags: List<WordTagEntity>,

    @Relation(parentColumn = "id", entityColumn = "word_id")
    val associations: List<WordAssociationEntity>,

    @Relation(parentColumn = "id", entityColumn = "word_id")
    val userMeta: WordUserMetaEntity?
)
