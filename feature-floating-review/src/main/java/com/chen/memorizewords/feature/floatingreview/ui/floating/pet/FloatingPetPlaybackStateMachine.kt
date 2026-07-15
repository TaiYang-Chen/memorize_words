package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

internal enum class FloatingPetVisibilityDirective {
    NONE,
    OPEN,
    CLOSE,
    REVERSE_TO_VISIBLE,
    REVERSE_TO_IDLE
}

internal enum class FloatingPetRecoveryDirective {
    RETRY_VISIBLE_LOOP,
    RETRY_IDLE,
    HOLD_VISIBLE,
    HOLD_IDLE
}

internal class FloatingPetPlaybackStateMachine {
    var state: FloatingPetPlaybackState = FloatingPetPlaybackState.UNINITIALIZED
        private set

    fun transitionTo(nextState: FloatingPetPlaybackState) {
        state = nextState
    }

    fun visibilityDirective(requestedVisible: Boolean): FloatingPetVisibilityDirective {
        return if (requestedVisible) {
            when (state) {
                FloatingPetPlaybackState.OPENING,
                FloatingPetPlaybackState.VISIBLE_LOOP,
                FloatingPetPlaybackState.SWITCHING_PACK -> FloatingPetVisibilityDirective.NONE
                FloatingPetPlaybackState.CLOSING ->
                    FloatingPetVisibilityDirective.REVERSE_TO_VISIBLE
                FloatingPetPlaybackState.UNINITIALIZED,
                FloatingPetPlaybackState.IDLE,
                FloatingPetPlaybackState.OPTIONAL -> FloatingPetVisibilityDirective.OPEN
                FloatingPetPlaybackState.RELEASED -> FloatingPetVisibilityDirective.NONE
            }
        } else {
            when (state) {
                FloatingPetPlaybackState.IDLE,
                FloatingPetPlaybackState.CLOSING,
                FloatingPetPlaybackState.UNINITIALIZED,
                FloatingPetPlaybackState.SWITCHING_PACK,
                FloatingPetPlaybackState.RELEASED -> FloatingPetVisibilityDirective.NONE
                FloatingPetPlaybackState.OPENING -> FloatingPetVisibilityDirective.REVERSE_TO_IDLE
                FloatingPetPlaybackState.VISIBLE_LOOP,
                FloatingPetPlaybackState.OPTIONAL -> FloatingPetVisibilityDirective.CLOSE
            }
        }
    }

    fun recoveryDirective(
        requestedVisible: Boolean,
        retryAlreadyAttempted: Boolean
    ): FloatingPetRecoveryDirective {
        return when {
            requestedVisible && !retryAlreadyAttempted ->
                FloatingPetRecoveryDirective.RETRY_VISIBLE_LOOP
            !requestedVisible && !retryAlreadyAttempted ->
                FloatingPetRecoveryDirective.RETRY_IDLE
            requestedVisible -> FloatingPetRecoveryDirective.HOLD_VISIBLE
            else -> FloatingPetRecoveryDirective.HOLD_IDLE
        }
    }
}
