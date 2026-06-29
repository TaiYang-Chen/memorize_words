package com.chen.memorizewords.data.wordbook.local.room.model.words.form

import com.chen.memorizewords.domain.word.model.word.WordForm
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordFormDto
import com.chen.memorizewords.domain.word.model.enums.FormType as DomainFormType

fun WordFormEntity.toDomain(): WordForm {
    return WordForm(
        id = id,
        wordId = wordId,
        formWordId = formWordId,
        formType = formType.toDomain(),
        formText = formText,
        formDefinition = formDefinition
    )
}

fun WordFormDto.toEntity(): WordFormEntity {
    return WordFormEntity(
        id = id,
        wordId = wordId,
        formWordId = formWordId,
        formType = WordFormEntity.FormType.fromString(formType) ?: WordFormEntity.FormType.OTHER,
        formText = formText,
        formDefinition = formDefinition
    )
}

fun WordForm.toEntity(): WordFormEntity {
    return WordFormEntity(
        id = id,
        wordId = wordId,
        formWordId = formWordId,
        formType = formType.toEntity(),
        formText = formText,
        formDefinition = formDefinition
    )
}

fun WordFormEntity.FormType.toDomain(): DomainFormType {
    return when (this) {
        WordFormEntity.FormType.NOUN,
        WordFormEntity.FormType.NOUN_FORM -> DomainFormType.NOUN
        WordFormEntity.FormType.VERB_FORM -> DomainFormType.VERB
        WordFormEntity.FormType.ADJECTIVE_FORM -> DomainFormType.ADJECTIVE
        WordFormEntity.FormType.ADVERB_FORM -> DomainFormType.ADVERB
        else -> DomainFormType.fromString(this.name)
    }
}

fun DomainFormType.toEntity(): WordFormEntity.FormType {
    return when (this) {
        DomainFormType.NOUN -> WordFormEntity.FormType.NOUN
        DomainFormType.VERB -> WordFormEntity.FormType.VERB_FORM
        DomainFormType.ADJECTIVE -> WordFormEntity.FormType.ADJECTIVE_FORM
        DomainFormType.ADVERB -> WordFormEntity.FormType.ADVERB_FORM
        else -> WordFormEntity.FormType.fromString(this.name) ?: WordFormEntity.FormType.OTHER
    }
}
