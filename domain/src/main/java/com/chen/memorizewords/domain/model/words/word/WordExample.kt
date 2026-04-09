package com.chen.memorizewords.domain.model.words.word

data class WordExample(
    val id: Long,
    val wordId: Long,
    val definitionId: Long? = null,
    val englishSentence: String,
    val chineseTranslation: String? = null,
    val difficultyLevel: DifficultyLevel = DifficultyLevel.MEDIUM
) {
    enum class DifficultyLevel {
        VERY_EASY, EASY, MEDIUM, HARD, VERY_HARD, EXPERT;

        companion object {
            fun fromInt(value: Int): DifficultyLevel {
                return when (value) {
                    1 -> VERY_EASY
                    2 -> EASY
                    3 -> MEDIUM
                    4 -> HARD
                    5 -> VERY_HARD
                    6 -> EXPERT
                    else -> MEDIUM
                }
            }
        }
    }
}