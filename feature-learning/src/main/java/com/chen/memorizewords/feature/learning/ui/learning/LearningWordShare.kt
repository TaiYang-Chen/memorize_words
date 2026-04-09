package com.chen.memorizewords.feature.learning.ui.learning

import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.domain.query.word.WordDetail

sealed interface LearningShareAction {
    data object Copy : LearningShareAction
}

data class LearningShareActionItem(
    val action: LearningShareAction,
    val title: String
)

sealed interface LearningShareEffect : UiEffect {
    data class ShowWordShareSheet(
        val actions: List<LearningShareActionItem>
    ) : LearningShareEffect

    data class CopyWordShareText(
        val text: String
    ) : LearningShareEffect
}

data class LearningWordShareLabels(
    val usPhoneticLabel: String,
    val ukPhoneticLabel: String,
    val definitionsTitle: String,
    val examplesTitle: String,
    val emptyPhonetic: String,
    val emptyDefinitions: String,
    val emptyExamples: String
)

internal fun buildLearningWordShareText(
    detail: WordDetail,
    labels: LearningWordShareLabels
): String {
    val definitionLines = detail.definitions
        .map(::buildDefinitionLine)
        .ifEmpty { listOf(labels.emptyDefinitions) }
    val exampleBlocks = detail.examples
        .map(::buildExampleBlock)
        .ifEmpty { listOf(labels.emptyExamples) }

    return listOf(
        detail.word.word,
        "${labels.usPhoneticLabel}: ${detail.word.phoneticUS?.takeIf { it.isNotBlank() } ?: labels.emptyPhonetic}",
        "${labels.ukPhoneticLabel}: ${detail.word.phoneticUK?.takeIf { it.isNotBlank() } ?: labels.emptyPhonetic}",
        labels.definitionsTitle,
        definitionLines.joinToString(separator = "\n"),
        labels.examplesTitle,
        exampleBlocks.joinToString(separator = "\n\n")
    ).joinToString(separator = "\n\n")
}

private fun buildDefinitionLine(definition: WordDefinitions): String {
    return "${definition.partOfSpeech.abbr} ${definition.meaningChinese}"
}

private fun buildExampleBlock(example: WordExample): String {
    val lines = buildList {
        add(example.englishSentence)
        example.chineseTranslation
            ?.takeIf { it.isNotBlank() }
            ?.let(::add)
    }
    return lines.joinToString(separator = "\n")
}
