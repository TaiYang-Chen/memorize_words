package com.chen.memorizewords.data.repository.wordbook

import androidx.room.withTransaction
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.model.wordbook.words.WordBookItemEntity
import com.chen.memorizewords.network.dto.wordbook.WordDto
import com.chen.memorizewords.data.local.room.model.words.word.toAntonymEntities as wordToAntonymEntities
import com.chen.memorizewords.data.local.room.model.words.word.toAssociationEntities as wordToAssociationEntities
import com.chen.memorizewords.data.local.room.model.words.word.toEntity as wordToEntity
import com.chen.memorizewords.data.local.room.model.words.word.toSynonymEntities as wordToSynonymEntities
import com.chen.memorizewords.data.local.room.model.words.word.toTagEntities as wordToTagEntities
import com.chen.memorizewords.data.local.room.model.words.word.toUserMetaEntity as wordToUserMetaEntity
import com.chen.memorizewords.data.local.room.model.words.definition.toEntity as definitionToEntity
import com.chen.memorizewords.data.local.room.model.words.example.toEntity as exampleToEntity
import com.chen.memorizewords.data.local.room.model.words.form.toEntity as formToEntity
import com.chen.memorizewords.data.local.room.model.words.root.root.toEntity as wordRootToEntity
import com.chen.memorizewords.data.local.room.model.words.root.rootword.toRelationEntity

internal suspend fun AppDatabase.persistWordBookPage(
    bookId: Long,
    items: List<WordDto>
) {
    if (items.isEmpty()) return

    val wordDao = wordDao()
    val bookWordItemDao = wordBookItemDao()
    val wordDefinitionDao = wordDefinitionDao()
    val wordExampleDao = wordExampleDao()
    val wordFormDao = wordFormDao()
    val wordRootDao = wordRootDao()
    val rootWordDao = rootWordDao()
    val wordRelationDao = wordRelationDao()
    val wordUserMetaDao = wordUserMetaDao()

    withTransaction {
        items.forEach { word ->
            wordDao.insert(word.wordToEntity())

            bookWordItemDao.insert(
                WordBookItemEntity(
                    wordBookId = bookId,
                    wordId = word.id
                )
            )

            val definitions = word.definitionDtos.map { it.definitionToEntity() }
            val examples = word.exampleDtos.map { it.exampleToEntity() }
            val forms = word.wordFormDtos.map { it.formToEntity() }
            val roots = word.rootWords.map { it.wordRootToEntity() }
            val rootRelations = word.rootWords.mapIndexed { index, root ->
                root.toRelationEntity(wordId = word.id, sequence = index + 1)
            }
            val synonyms = word.wordToSynonymEntities()
            val antonyms = word.wordToAntonymEntities()
            val tags = word.wordToTagEntities()
            val associations = word.wordToAssociationEntities()

            wordDefinitionDao.deleteByWordId(word.id)
            wordExampleDao.deleteByWordId(word.id)
            wordFormDao.deleteByWordId(word.id)
            wordDefinitionDao.insert(definitions)
            wordExampleDao.insert(examples)
            wordFormDao.insert(forms)
            wordRootDao.insertRoots(roots)
            rootWordDao.deleteByWordId(word.id)
            rootWordDao.insert(rootRelations)

            wordRelationDao.deleteSynonymsByWordId(word.id)
            wordRelationDao.deleteAntonymsByWordId(word.id)
            wordRelationDao.deleteTagsByWordId(word.id)
            wordRelationDao.deleteAssociationsByWordId(word.id)
            if (synonyms.isNotEmpty()) wordRelationDao.insertSynonyms(synonyms)
            if (antonyms.isNotEmpty()) wordRelationDao.insertAntonyms(antonyms)
            if (tags.isNotEmpty()) wordRelationDao.insertTags(tags)
            if (associations.isNotEmpty()) wordRelationDao.insertAssociations(associations)

            wordUserMetaDao.upsert(word.wordToUserMetaEntity())
        }
    }
}
