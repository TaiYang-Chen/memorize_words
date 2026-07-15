package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

import kotlin.test.Test
import kotlin.test.assertEquals

class FloatingPetPlaybackStateMachineTest {
    private val stateMachine = FloatingPetPlaybackStateMachine()

    @Test
    fun `opening reverses toward idle when visibility is withdrawn`() {
        stateMachine.transitionTo(FloatingPetPlaybackState.OPENING)

        assertEquals(
            FloatingPetVisibilityDirective.REVERSE_TO_IDLE,
            stateMachine.visibilityDirective(requestedVisible = false)
        )
    }

    @Test
    fun `closing reverses toward visible when reopened`() {
        stateMachine.transitionTo(FloatingPetPlaybackState.CLOSING)

        assertEquals(
            FloatingPetVisibilityDirective.REVERSE_TO_VISIBLE,
            stateMachine.visibilityDirective(requestedVisible = true)
        )
    }

    @Test
    fun `repeated visibility commands do not restart active actions`() {
        stateMachine.transitionTo(FloatingPetPlaybackState.OPENING)
        assertEquals(
            FloatingPetVisibilityDirective.NONE,
            stateMachine.visibilityDirective(requestedVisible = true)
        )

        stateMachine.transitionTo(FloatingPetPlaybackState.CLOSING)
        assertEquals(
            FloatingPetVisibilityDirective.NONE,
            stateMachine.visibilityDirective(requestedVisible = false)
        )
    }

    @Test
    fun `stable states choose the matching transition`() {
        stateMachine.transitionTo(FloatingPetPlaybackState.IDLE)
        assertEquals(
            FloatingPetVisibilityDirective.OPEN,
            stateMachine.visibilityDirective(requestedVisible = true)
        )

        stateMachine.transitionTo(FloatingPetPlaybackState.VISIBLE_LOOP)
        assertEquals(
            FloatingPetVisibilityDirective.CLOSE,
            stateMachine.visibilityDirective(requestedVisible = false)
        )
    }

    @Test
    fun `playback recovery retries once and then holds a stable state`() {
        assertEquals(
            FloatingPetRecoveryDirective.RETRY_VISIBLE_LOOP,
            stateMachine.recoveryDirective(
                requestedVisible = true,
                retryAlreadyAttempted = false
            )
        )
        assertEquals(
            FloatingPetRecoveryDirective.HOLD_VISIBLE,
            stateMachine.recoveryDirective(
                requestedVisible = true,
                retryAlreadyAttempted = true
            )
        )
        assertEquals(
            FloatingPetRecoveryDirective.RETRY_IDLE,
            stateMachine.recoveryDirective(
                requestedVisible = false,
                retryAlreadyAttempted = false
            )
        )
    }
}
