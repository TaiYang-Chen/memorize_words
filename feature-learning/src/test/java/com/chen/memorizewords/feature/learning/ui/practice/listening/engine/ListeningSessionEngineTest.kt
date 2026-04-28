package com.chen.memorizewords.feature.learning.ui.practice.listening.engine

import com.chen.memorizewords.feature.learning.ui.practice.ListeningPracticeMode
import com.chen.memorizewords.feature.learning.ui.practice.ListeningQuestionType
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_MEANING_SELECTION_TRANSITION_DELAY_MS
import com.chen.memorizewords.feature.learning.ui.practice.listening.ListeningAction
import com.chen.memorizewords.feature.learning.ui.practice.listening.ListeningEffect
import com.chen.memorizewords.feature.learning.ui.practice.listening.ListeningSessionConfig
import com.chen.memorizewords.feature.learning.ui.practice.listening.ListeningSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningSessionEngineTest {

    private val engine = ListeningSessionEngine()

    @Test
    fun `start session stores config and marks session started`() {
        val config = ListeningSessionConfig(
            selectedIds = longArrayOf(1L, 2L),
            randomCount = 20,
            mode = ListeningPracticeMode.MEANING
        )

        val result = engine.reduce(
            state = ListeningSessionState(),
            action = ListeningAction.StartSession(config)
        )

        assertTrue(result.state.hasStarted)
        assertEquals(config, result.state.config)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `select meaning emits delayed advance effect for active meaning card`() {
        val state = ListeningSessionState(
            activeWordId = 9L,
            activeQuestionType = ListeningQuestionType.MEANING
        )

        val result = engine.reduce(state, ListeningAction.SelectMeaning(index = 1))

        assertTrue(result.state.isTransitionPending)
        assertEquals(
            listOf(
                ListeningEffect.DelayThenAdvance(
                    delayMs = LISTENING_MEANING_SELECTION_TRANSITION_DELAY_MS,
                    expectedWordId = 9L,
                    expectedQuestionType = ListeningQuestionType.MEANING
                )
            ),
            result.effects
        )
    }

    @Test
    fun `change mode clears pending transition and preserves config`() {
        val state = ListeningSessionState(
            config = ListeningSessionConfig(
                selectedIds = longArrayOf(1L),
                randomCount = 20,
                mode = ListeningPracticeMode.MEANING
            ),
            hasStarted = true,
            activeWordId = 1L,
            isTransitionPending = true
        )

        val result = engine.reduce(state, ListeningAction.ChangeMode(ListeningPracticeMode.SPELLING))

        assertFalse(result.state.isTransitionPending)
        assertEquals(ListeningPracticeMode.SPELLING, result.state.config?.mode)
        assertTrue(result.state.hasStarted)
    }
}
