package com.chen.memorizewords.domain.study.model.favorites

import com.chen.memorizewords.domain.word.model.enums.PartOfSpeech

object FavoriteDefinitionFormatter {
    private val whitespaceRegex = Regex("\\s+")

    fun abbreviatePartsOfSpeech(definitions: String): String {
        return definitions
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.split(whitespaceRegex)
            ?.joinToString(separator = " ") { token ->
                token.toPartOfSpeechAbbr() ?: token
            }
            .orEmpty()
    }

    private fun String.toPartOfSpeechAbbr(): String? {
        val partOfSpeech = PartOfSpeech.fromString(this)
        return partOfSpeech
            .takeIf { it != PartOfSpeech.OTHER && it != PartOfSpeech.UNKNOWN }
            ?.abbr
    }
}
