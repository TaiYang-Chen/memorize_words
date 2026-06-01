package com.chen.memorizewords.domain.practice
enum class PracticeKind {
    LISTENING_MEANING,
    LISTENING_SPELLING,
    SHADOWING,
    AUDIO_LOOP,
    EXAM
}

enum class PracticePhase {
    IDLE,
    ANSWERING,
    FEEDBACK,
    STUDYING,
    REPORT
}

enum class PracticeAnswerStatus {
    CORRECT,
    WRONG,
    REVEALED,
    SKIPPED
}

data class PracticeWord(
    val id: Long,
    val text: String,
    val definitions: List<String> = emptyList(),
    val phoneticUs: String? = null,
    val phoneticUk: String? = null,
    val examples: List<String> = emptyList()
)

data class PracticeChoice(
    val id: String,
    val text: String,
    val isCorrect: Boolean
)

sealed interface PracticeQuestion {
    val id: String
    val kind: PracticeKind
    val word: PracticeWord
    val speechLocale: String?
    fun answerLabel(): String
}

data class MeaningChoiceQuestion(
    override val id: String,
    override val kind: PracticeKind,
    override val word: PracticeWord,
    val choices: List<PracticeChoice>,
    override val speechLocale: String? = DEFAULT_SPEECH_LOCALE
) : PracticeQuestion {
    override fun answerLabel(): String =
        choices.firstOrNull { it.isCorrect }?.text.orEmpty()
}

data class SpellingQuestion(
    override val id: String,
    override val word: PracticeWord,
    val answer: String = word.text,
    override val speechLocale: String? = DEFAULT_SPEECH_LOCALE
) : PracticeQuestion {
    override val kind: PracticeKind = PracticeKind.LISTENING_SPELLING
    override fun answerLabel(): String = answer
}

data class ShadowingQuestion(
    override val id: String,
    override val word: PracticeWord,
    val prompt: String = word.text,
    override val speechLocale: String? = DEFAULT_SPEECH_LOCALE
) : PracticeQuestion {
    override val kind: PracticeKind = PracticeKind.SHADOWING
    override fun answerLabel(): String = prompt
}

data class AudioLoopQuestion(
    override val id: String,
    override val word: PracticeWord,
    override val speechLocale: String? = DEFAULT_SPEECH_LOCALE
) : PracticeQuestion {
    override val kind: PracticeKind = PracticeKind.AUDIO_LOOP
    override fun answerLabel(): String = word.text
}

data class PracticeAnswerRecord(
    val questionId: String,
    val wordId: Long,
    val status: PracticeAnswerStatus,
    val submittedAnswer: String? = null,
    val expectedAnswer: String,
    val score: Float? = null
)

data class PracticeReport(
    val totalQuestionCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val skippedCount: Int,
    val revealedCount: Int,
    val accuracyPercent: Int
)

data class PracticeSession(
    val sessionId: String,
    val kind: PracticeKind,
    val phase: PracticePhase = PracticePhase.IDLE,
    val activeQuestion: PracticeQuestion? = null,
    val pendingQuestions: List<PracticeQuestion> = emptyList(),
    val reviewQuestions: List<PracticeQuestion> = emptyList(),
    val history: List<PracticeAnswerRecord> = emptyList(),
    val report: PracticeReport? = null,
    val totalQuestionCount: Int = 0
)

const val DEFAULT_SPEECH_LOCALE = "en-US"
