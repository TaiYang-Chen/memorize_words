package com.chen.memorizewords.domain.practice
interface PracticePlugin {
    val kind: PracticeKind
    fun buildQuestion(word: PracticeWord, index: Int, allWords: List<PracticeWord>): PracticeQuestion
}

class MeaningChoicePracticePlugin(
    override val kind: PracticeKind = PracticeKind.LISTENING_MEANING
) : PracticePlugin {
    override fun buildQuestion(
        word: PracticeWord,
        index: Int,
        allWords: List<PracticeWord>
    ): PracticeQuestion {
        val correctText = word.definitions.firstOrNull().orEmpty()
        val distractors = allWords.asSequence()
            .filter { candidate -> candidate.id != word.id }
            .mapNotNull { candidate -> candidate.definitions.firstOrNull() }
            .filter { definition -> definition.isNotBlank() && definition != correctText }
            .distinct()
            .take(MAX_CHOICE_COUNT - 1)
            .mapIndexed { distractorIndex, text ->
                PracticeChoice(
                    id = "d_${word.id}_$distractorIndex",
                    text = text,
                    isCorrect = false
                )
            }
            .toList()
        return MeaningChoiceQuestion(
            id = questionId(kind, word, index),
            kind = kind,
            word = word,
            choices = listOf(
                PracticeChoice(
                    id = "c_${word.id}",
                    text = correctText,
                    isCorrect = true
                )
            ) + distractors
        )
    }

    private companion object {
        const val MAX_CHOICE_COUNT = 4
    }
}

class SpellingPracticePlugin : PracticePlugin {
    override val kind: PracticeKind = PracticeKind.LISTENING_SPELLING

    override fun buildQuestion(
        word: PracticeWord,
        index: Int,
        allWords: List<PracticeWord>
    ): PracticeQuestion {
        return SpellingQuestion(
            id = questionId(kind, word, index),
            word = word
        )
    }
}

class ShadowingPracticePlugin : PracticePlugin {
    override val kind: PracticeKind = PracticeKind.SHADOWING

    override fun buildQuestion(
        word: PracticeWord,
        index: Int,
        allWords: List<PracticeWord>
    ): PracticeQuestion {
        return ShadowingQuestion(
            id = questionId(kind, word, index),
            word = word
        )
    }
}

class AudioLoopPracticePlugin : PracticePlugin {
    override val kind: PracticeKind = PracticeKind.AUDIO_LOOP

    override fun buildQuestion(
        word: PracticeWord,
        index: Int,
        allWords: List<PracticeWord>
    ): PracticeQuestion {
        return AudioLoopQuestion(
            id = questionId(kind, word, index),
            word = word
        )
    }
}

class ExamPracticePlugin : PracticePlugin {
    private val delegate = MeaningChoicePracticePlugin(PracticeKind.EXAM)
    override val kind: PracticeKind = PracticeKind.EXAM

    override fun buildQuestion(
        word: PracticeWord,
        index: Int,
        allWords: List<PracticeWord>
    ): PracticeQuestion = delegate.buildQuestion(word, index, allWords)
}

internal fun questionId(kind: PracticeKind, word: PracticeWord, index: Int): String {
    return "${kind.name}:${word.id}:$index"
}
