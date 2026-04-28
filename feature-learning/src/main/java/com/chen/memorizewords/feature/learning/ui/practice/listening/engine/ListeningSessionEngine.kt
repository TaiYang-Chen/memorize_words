package com.chen.memorizewords.feature.learning.ui.practice.listening.engine

import com.chen.memorizewords.feature.learning.ui.practice.ListeningQuestionType
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_MEANING_SELECTION_TRANSITION_DELAY_MS
import com.chen.memorizewords.feature.learning.ui.practice.listening.ListeningAction
import com.chen.memorizewords.feature.learning.ui.practice.listening.ListeningEffect
import com.chen.memorizewords.feature.learning.ui.practice.listening.ListeningReduceResult
import com.chen.memorizewords.feature.learning.ui.practice.listening.ListeningSessionState

internal class ListeningSessionEngine {
    fun reduce(
        state: ListeningSessionState,
        action: ListeningAction
    ): ListeningReduceResult {
        return when (action) {
            is ListeningAction.StartSession -> {
                ListeningReduceResult(
                    state = ListeningSessionState(
                        config = action.config,
                        hasStarted = true
                    )
                )
            }

            is ListeningAction.ChangeMode -> {
                ListeningReduceResult(
                    state = state.copy(
                        config = state.config?.copy(mode = action.mode),
                        isTransitionPending = false
                    )
                )
            }

            is ListeningAction.SelectMeaning -> {
                val wordId = state.activeWordId
                if (wordId == null || state.activeQuestionType != ListeningQuestionType.MEANING) {
                    ListeningReduceResult(state)
                } else {
                    ListeningReduceResult(
                        state = state.copy(isTransitionPending = true),
                        effects = listOf(
                            ListeningEffect.DelayThenAdvance(
                                delayMs = LISTENING_MEANING_SELECTION_TRANSITION_DELAY_MS,
                                expectedWordId = wordId,
                                expectedQuestionType = ListeningQuestionType.MEANING
                            )
                        )
                    )
                }
            }

            ListeningAction.DeleteLastSpellingLetter,
            ListeningAction.SubmitSpelling,
            ListeningAction.Skip,
            ListeningAction.RevealAnswer,
            ListeningAction.ContinueAfterStudy,
            ListeningAction.ToggleStudyPronunciation,
            is ListeningAction.SelectSpellingLetter -> ListeningReduceResult(state)
        }
    }
}
