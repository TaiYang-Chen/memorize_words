package com.chen.memorizewords.domain.model.words.enums

/**
 * Part of speech enum with tolerant string parsing.
 */
enum class PartOfSpeech(val abbr: String) {

    UNKNOWN("unknow"),
    NOUN("n."),
    VERB("v."),
    ADJECTIVE("adj."),
    ADVERB("adv."),
    PRONOUN("pron."),
    PREPOSITION("prep."),
    CONJUNCTION("conj."),
    INTERJECTION("interj."),
    DETERMINER("det."),
    NUMERAL("num."),
    PHRASAL_VERB("phr v."),
    IDIOM("idiom."),
    PREFIX("pref."),
    SUFFIX("suf."),
    ABBREVIATION("abbr."),
    ACRONYM("acronym."),
    OTHER("other.");

    companion object {
        fun fromString(value: String): PartOfSpeech {
            val raw = value.trim()
            if (raw.isEmpty()) return OTHER

            val normalized = normalize(raw)
            return entries.firstOrNull {
                normalize(it.name) == normalized || normalize(it.abbr) == normalized
            } ?: aliasLookup(normalized) ?: OTHER
        }

        private fun normalize(value: String): String {
            return value
                .lowercase()
                .replace("_", "")
                .replace(" ", "")
                .replace(".", "")
        }

        private fun aliasLookup(value: String): PartOfSpeech? {
            return when (value) {
                "n", "noun" -> NOUN
                "v", "verb", "vt", "vi" -> VERB
                "adj", "adjective" -> ADJECTIVE
                "adv", "adverb" -> ADVERB
                "pron", "pronoun" -> PRONOUN
                "prep", "preposition" -> PREPOSITION
                "conj", "conjunction" -> CONJUNCTION
                "interj", "interjection", "int" -> INTERJECTION
                "det", "determiner", "art", "article" -> DETERMINER
                "num", "numeral" -> NUMERAL
                "phrv", "phrasalverb" -> PHRASAL_VERB
                "idiom" -> IDIOM
                "pref", "prefix" -> PREFIX
                "suf", "suffix" -> SUFFIX
                "abbr", "abbreviation" -> ABBREVIATION
                "acronym" -> ACRONYM
                "unknow", "unknown", "unk" -> UNKNOWN
                "other" -> OTHER
                else -> null
            }
        }
    }
}
