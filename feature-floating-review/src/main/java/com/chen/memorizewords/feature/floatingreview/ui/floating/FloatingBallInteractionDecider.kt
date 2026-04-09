package com.chen.memorizewords.feature.floatingreview.ui.floating

internal sealed interface FloatingBallSingleTapAction {
    data object NoOp : FloatingBallSingleTapAction
    data object ShowCard : FloatingBallSingleTapAction
    data object HideCard : FloatingBallSingleTapAction
}

internal sealed interface FloatingBallDoubleTapAction {
    data object HideToNearestEdge : FloatingBallDoubleTapAction
    data object RevealDocked : FloatingBallDoubleTapAction
}

internal fun resolveSingleTapAction(
    isDocked: Boolean,
    isCardVisible: Boolean
): FloatingBallSingleTapAction {
    return when {
        isDocked -> FloatingBallSingleTapAction.NoOp
        isCardVisible -> FloatingBallSingleTapAction.HideCard
        else -> FloatingBallSingleTapAction.ShowCard
    }
}

internal fun resolveDoubleTapAction(
    isDocked: Boolean
): FloatingBallDoubleTapAction {
    return if (isDocked) {
        FloatingBallDoubleTapAction.RevealDocked
    } else {
        FloatingBallDoubleTapAction.HideToNearestEdge
    }
}
