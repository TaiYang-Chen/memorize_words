package com.chen.memorizewords.data.wordbook.repository.wordbook

import com.chen.memorizewords.data.wordbook.local.room.model.words.example.WordExampleEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.form.WordFormEntity

internal fun sanitizeWordForms(
    forms: List<WordFormEntity>,
    validWordIds: Set<Long>
): List<WordFormEntity> {
    return forms.map { form ->
        val formWordId = form.formWordId
        if (formWordId == null || formWordId in validWordIds) {
            form
        } else {
            form.copy(formWordId = null)
        }
    }
}

internal fun sanitizeWordExamples(
    examples: List<WordExampleEntity>,
    validDefinitionIds: Set<Long>
): List<WordExampleEntity> {
    return examples.map { example ->
        val definitionId = example.definitionId
        if (definitionId == null || definitionId in validDefinitionIds) {
            example
        } else {
            example.copy(definitionId = null)
        }
    }
}
