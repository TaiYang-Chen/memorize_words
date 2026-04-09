package com.chen.memorizewords.data.local.room.model.words.word

import com.chen.memorizewords.data.local.room.model.words.meta.WordUserMetaEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordAntonymEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordAssociationEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordSynonymEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordTagEntity
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.network.dto.wordbook.WordDto

fun Word.toEntity() = WordEntity(
    id = id,
    word = word,
    normalizedWord = normalizedWord,
    phoneticUS = phoneticUS,
    phoneticUK = phoneticUK,
    hasIrregularForms = hasIrregularForms,
    wordFamily = wordFamily
)

fun WordWithRelations.toDomain(): Word {
    val meta = userMeta
    return Word(
        id = word.id,
        word = word.word,
        normalizedWord = word.normalizedWord,
        phoneticUS = word.phoneticUS,
        phoneticUK = word.phoneticUK,
        hasIrregularForms = word.hasIrregularForms,
        memoryTip = meta?.memoryTip,
        mnemonicImageUrl = meta?.mnemonicImageUrl,
        memoryAssociations = associations.map { it.value },
        wordFamily = word.wordFamily,
        synonyms = synonyms.map { it.value },
        antonyms = antonyms.map { it.value },
        tags = tags.map { it.value },
        notes = meta?.notes,
        rootMemoryTip = meta?.rootMemoryTip
    )
}

fun WordDto.toEntity(): WordEntity {
    return WordEntity(
        id = id,
        word = word,
        normalizedWord = normalizedWord,
        phoneticUS = phoneticUS,
        phoneticUK = phoneticUK,
        hasIrregularForms = hasIrregularForms,
        wordFamily = wordFamily
    )
}

fun WordDto.toUserMetaEntity(): WordUserMetaEntity {
    return WordUserMetaEntity(
        wordId = id,
        memoryTip = memoryTip,
        mnemonicImageUrl = mnemonicImageUrl,
        notes = notes,
        rootMemoryTip = rootMemoryTip,
        isUserSelected = false
    )
}

fun WordDto.toSynonymEntities(): List<WordSynonymEntity> {
    return synonyms
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { normalizeToken(it) }
        .map { value ->
            WordSynonymEntity(
                wordId = id,
                value = value,
                normalizedValue = normalizeToken(value)
            )
        }
        .toList()
}

fun WordDto.toAntonymEntities(): List<WordAntonymEntity> {
    return antonyms
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { normalizeToken(it) }
        .map { value ->
            WordAntonymEntity(
                wordId = id,
                value = value,
                normalizedValue = normalizeToken(value)
            )
        }
        .toList()
}

fun WordDto.toTagEntities(): List<WordTagEntity> {
    return tags
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { normalizeToken(it) }
        .map { value ->
            WordTagEntity(
                wordId = id,
                value = value,
                normalizedValue = normalizeToken(value)
            )
        }
        .toList()
}

fun WordDto.toAssociationEntities(): List<WordAssociationEntity> {
    return memoryAssociations
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { normalizeToken(it) }
        .map { value ->
            WordAssociationEntity(
                wordId = id,
                value = value,
                normalizedValue = normalizeToken(value)
            )
        }
        .toList()
}

private fun normalizeToken(value: String): String {
    return value.lowercase().trim()
}
