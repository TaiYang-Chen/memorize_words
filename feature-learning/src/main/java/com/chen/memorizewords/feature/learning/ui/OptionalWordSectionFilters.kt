package com.chen.memorizewords.feature.learning.ui

import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.domain.word.model.word.WordForm
import com.chen.memorizewords.domain.word.model.word.WordRoot

internal fun List<String>.visibleRelationWords(): List<String> {
    return map { it.visibleRelationWordText() }
        .filter { it.isNotEmpty() }
        .distinct()
}

private fun String.visibleRelationWordText(): String {
    return filterNot { it.isInvisibleRelationCharacter() }.trim()
}

private fun Char.isInvisibleRelationCharacter(): Boolean {
    return when (Character.getType(this)) {
        Character.CONTROL.toInt(),
        Character.FORMAT.toInt(),
        Character.SURROGATE.toInt(),
        Character.UNASSIGNED.toInt() -> true
        else -> false
    }
}

internal fun List<WordExample>.visibleWordExamples(): List<WordExample> {
    return filter { example ->
        example.englishSentence.isNotBlank() ||
            example.chineseTranslation?.isNotBlank() == true
    }
}

internal fun List<WordForm>.visibleWordForms(): List<WordForm> {
    return filter { it.formText.isNotBlank() }
}

internal fun List<WordRoot>.visibleWordRoots(): List<WordRoot> {
    return filter { root ->
        root.rootWord.isNotBlank() ||
            root.coreMeaning.isNotBlank() ||
            root.etymology?.isNotBlank() == true ||
            root.sourceLanguage.isNotBlank() ||
            root.meanings.isNotEmpty() ||
            root.variants.isNotEmpty()
    }
}
