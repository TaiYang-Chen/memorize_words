package com.chen.memorizewords.domain.practice
sealed interface PracticeAction {
    data class Start(
        val sessionId: String,
        val kind: PracticeKind,
        val words: List<PracticeWord>
    ) : PracticeAction

    data class SubmitChoice(
        val questionId: String,
        val choiceId: String
    ) : PracticeAction

    data class SubmitText(
        val questionId: String,
        val text: String
    ) : PracticeAction

    data class SubmitShadowing(
        val questionId: String,
        val passed: Boolean,
        val score: Float? = null
    ) : PracticeAction

    data object RevealAnswer : PracticeAction
    data object Skip : PracticeAction
    data object Continue : PracticeAction
    data object Reset : PracticeAction
}

sealed interface PracticeEffect {
    data class RequestSpeech(
        val questionId: String,
        val wordId: Long,
        val text: String,
        val locale: String
    ) : PracticeEffect

    data class PlaySpeech(
        val questionId: String
    ) : PracticeEffect

    data class ShowFeedback(
        val questionId: String,
        val status: PracticeAnswerStatus
    ) : PracticeEffect

    data class ShowStudyCard(
        val questionId: String,
        val wordId: Long
    ) : PracticeEffect

    data class AdvanceAfterDelay(
        val questionId: String
    ) : PracticeEffect

    data class CompleteSession(
        val report: PracticeReport
    ) : PracticeEffect
}

data class PracticeReduceResult(
    val state: PracticeSession,
    val effects: List<PracticeEffect> = emptyList()
)
