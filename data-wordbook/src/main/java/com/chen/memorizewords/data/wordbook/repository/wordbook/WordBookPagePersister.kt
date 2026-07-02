package com.chen.memorizewords.data.wordbook.repository.wordbook

import androidx.room.withTransaction
import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words.WordBookItemEntity
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordDto
import javax.inject.Inject
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.toAntonymEntities as wordToAntonymEntities
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.toAssociationEntities as wordToAssociationEntities
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.toEntity as wordToEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.toSynonymEntities as wordToSynonymEntities
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.toTagEntities as wordToTagEntities
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.toUserMetaEntity as wordToUserMetaEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.definition.toEntity as definitionToEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.example.toEntity as exampleToEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.form.toEntity as formToEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.toTagEntities as rootToTagEntities
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.toEntity as wordRootToEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootword.toRelationEntity

class WordBookContentLocalStore @Inject constructor(
    private val database: WordBookDatabase
) {
    suspend fun persistPage(
        bookId: Long,
        items: List<WordDto>
    ) {
        database.persistWordBookPage(bookId, items)
    }
}

internal suspend fun WordBookDatabase.persistWordBookPage(
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
    val rootTagDao = rootTagDao()
    val rootWordDao = rootWordDao()
    val wordRelationDao = wordRelationDao()
    val wordUserMetaDao = wordUserMetaDao()

    withTransaction {
        val pageWordIds = items.map { it.id }.toSet()
        val referencedFormWordIds = items
            .flatMap { word -> word.wordFormDtos.mapNotNull { it.formWordId } }
            .filter { it > 0L }
            .toSet()
        val existingReferencedWordIds = referencedFormWordIds.chunkedSql()
            .flatMap { chunk -> wordDao.getByIds(chunk) }
            .map { it.id }
            .toSet()
        val validFormWordIds = pageWordIds + existingReferencedWordIds

        wordDao.insertAll(items.map { it.wordToEntity() })
        bookWordItemDao.insertAll(
            items.map { word ->
                WordBookItemEntity(
                    wordBookId = bookId,
                    wordId = word.id
                )
            }
        )

        val wordIds = pageWordIds.toList()
        val definitionsByWordId = items.associate { word ->
            word.id to word.definitionDtos.map { it.definitionToEntity() }
        }
        val definitions = definitionsByWordId.values.flatten()
        val examples = items.flatMap { word ->
            sanitizeWordExamples(
                examples = word.exampleDtos.map { it.exampleToEntity() },
                validDefinitionIds = definitionsByWordId[word.id].orEmpty().map { it.id }.toSet()
            )
        }
        val forms = items.flatMap { word ->
            sanitizeWordForms(
                forms = word.wordFormDtos.map { it.formToEntity() },
                validWordIds = validFormWordIds
            )
        }
        val roots = items.flatMap { word -> word.rootWords.map { it.wordRootToEntity() } }
        val rootTags = items.flatMap { word -> word.rootWords.flatMap { it.rootToTagEntities() } }
        val rootRelations = items.flatMap { word ->
            word.rootWords.mapIndexed { index, root ->
                root.toRelationEntity(wordId = word.id, sequence = index + 1)
            }
        }
        val synonyms = items.flatMap { it.wordToSynonymEntities() }
        val antonyms = items.flatMap { it.wordToAntonymEntities() }
        val tags = items.flatMap { it.wordToTagEntities() }
        val associations = items.flatMap { it.wordToAssociationEntities() }

        wordIds.chunkedSql().forEach { chunk ->
            wordDefinitionDao.deleteByWordIds(chunk)
            wordExampleDao.deleteByWordIds(chunk)
            wordFormDao.deleteByWordIds(chunk)
            rootWordDao.deleteByWordIds(chunk)
            wordRelationDao.deleteSynonymsByWordIds(chunk)
            wordRelationDao.deleteAntonymsByWordIds(chunk)
            wordRelationDao.deleteTagsByWordIds(chunk)
            wordRelationDao.deleteAssociationsByWordIds(chunk)
        }

        if (definitions.isNotEmpty()) wordDefinitionDao.insert(definitions)
        if (examples.isNotEmpty()) wordExampleDao.insert(examples)
        if (forms.isNotEmpty()) wordFormDao.insert(forms)
        if (roots.isNotEmpty()) wordRootDao.insertRoots(roots)
        roots.map { it.id }.chunkedSql().forEach { chunk -> rootTagDao.deleteByRootIds(chunk) }
        if (rootTags.isNotEmpty()) rootTagDao.insertAll(rootTags)
        if (rootRelations.isNotEmpty()) rootWordDao.insert(rootRelations)
        if (synonyms.isNotEmpty()) wordRelationDao.insertSynonyms(synonyms)
        if (antonyms.isNotEmpty()) wordRelationDao.insertAntonyms(antonyms)
        if (tags.isNotEmpty()) wordRelationDao.insertTags(tags)
        if (associations.isNotEmpty()) wordRelationDao.insertAssociations(associations)

        items.forEach { word ->
            wordUserMetaDao.upsert(word.wordToUserMetaEntity())
        }
    }
}

internal fun Collection<Long>.chunkedSql(): List<List<Long>> {
    if (isEmpty()) return emptyList()
    return toList().chunked(SQL_BIND_CHUNK_SIZE)
}

private const val SQL_BIND_CHUNK_SIZE = 500
