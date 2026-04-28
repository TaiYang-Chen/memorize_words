package com.chen.memorizewords.feature.floatingreview.ui.floating

internal sealed interface FloatingBallSingleTapAction {
    data object ShowCard : FloatingBallSingleTapAction
    data object HideCard : FloatingBallSingleTapAction
}

internal fun resolveSingleTapAction(
    isCardVisible: Boolean
): FloatingBallSingleTapAction {
    return if (isCardVisible) {
        FloatingBallSingleTapAction.HideCard
    } else {
        FloatingBallSingleTapAction.ShowCard
    }
}
