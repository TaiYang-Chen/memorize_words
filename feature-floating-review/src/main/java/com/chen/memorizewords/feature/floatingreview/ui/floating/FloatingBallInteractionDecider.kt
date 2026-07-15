package com.chen.memorizewords.feature.floatingreview.ui.floating

internal sealed interface FloatingBallSingleTapAction {
    data object ShowCard : FloatingBallSingleTapAction
    data object ShowNextCard : FloatingBallSingleTapAction
    data object HideCard : FloatingBallSingleTapAction
}

internal sealed interface FloatingCardCloseAction {
    data object HideCard : FloatingCardCloseAction
}

internal fun resolveSingleTapAction(
    isCardVisible: Boolean,
    hasCurrentWord: Boolean
): FloatingBallSingleTapAction {
    return when {
        isCardVisible -> FloatingBallSingleTapAction.HideCard
        hasCurrentWord -> FloatingBallSingleTapAction.ShowCard
        else -> FloatingBallSingleTapAction.ShowNextCard
    }
}

internal fun resolveCardCloseAction(): FloatingCardCloseAction {
    return FloatingCardCloseAction.HideCard
}

internal fun isRapidRepeatTap(
    previousEventTimeMillis: Long?,
    eventTimeMillis: Long,
    suppressionWindowMillis: Long
): Boolean {
    require(suppressionWindowMillis >= 0L)
    val previous = previousEventTimeMillis ?: return false
    return eventTimeMillis - previous in 0L..suppressionWindowMillis
}
