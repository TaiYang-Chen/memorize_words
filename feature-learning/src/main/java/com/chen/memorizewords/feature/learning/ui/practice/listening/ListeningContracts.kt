package com.chen.memorizewords.feature.learning.ui.practice.listening

import com.chen.memorizewords.feature.learning.ui.practice.ListeningPracticeMode
import com.chen.memorizewords.feature.learning.ui.practice.ListeningQuestionType

internal const val LISTENING_CORRECT_FEEDBACK_DURATION_MS = 650L
internal const val LISTENING_MEANING_SELECTION_TRANSITION_DELAY_MS = 500L
internal const val LISTENING_WRONG_MEANING_SELECTION_TRANSITION_DELAY_MS = 800L
internal const val LISTENING_WRONG_SPELLING_FEEDBACK_DURATION_MS = 1200L
internal const val LISTENING_SPEECH_LOCALE_US = "en-US"
internal const val LISTENING_SPEECH_LOCALE_UK = "en-GB"

internal data class ListeningSessionConfig(
    val selectedIds: LongArray?,
    val randomCount: Int,
    val mode: ListeningPracticeMode
) {
    val key: String = "${selectedIds?.joinToString(separator = ",") ?: "random:$randomCount"}_${mode.name}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ListeningSessionConfig) return false
        return randomCount == other.randomCount &&
            mode == other.mode &&
            selectedIds.contentEquals(other.selectedIds)
    }

    override fun hashCode(): Int {
        var result = selectedIds.contentHashCode()
        result = 31 * result + randomCount
        result = 31 * result + mode.hashCode()
        return result
    }
}

internal sealed interface ListeningAction {
    data class StartSession(val config: ListeningSessionConfig) : ListeningAction
    data class ChangeMode(val mode: ListeningPracticeMode) : ListeningAction
    data class PresentQuestion(
        val wordId: Long,
        val questionType: ListeningQuestionType
    ) : ListeningAction
    data class SelectMeaning(val index: Int) : ListeningAction
    data object ShowStudy : ListeningAction
    data object ShowReport : ListeningAction
    data class SelectSpellingLetter(val letterId: Long) : ListeningAction
    data object DeleteLastSpellingLetter : ListeningAction
    data object SubmitSpelling : ListeningAction
    data object Skip : ListeningAction
    data object RevealAnswer : ListeningAction
    data object ContinueAfterStudy : ListeningAction
    data object ToggleStudyPronunciation : ListeningAction
}

internal data class ListeningSessionState(
    val config: ListeningSessionConfig? = null,
    val hasStarted: Boolean = false,
    val screen: ListeningScreenState = ListeningScreenState.PRACTICE,
    val activeWordId: Long? = null,
    val activeQuestionType: ListeningQuestionType = ListeningQuestionType.MEANING,
    val isTransitionPending: Boolean = false
)

internal enum class ListeningScreenState {
    PRACTICE,
    STUDY,
    REPORT
}
