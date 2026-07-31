package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceKey
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceSnapshot
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType

internal data class FloatingWordSequenceState(
    val sourceKey: FloatingWordSourceKey? = null,
    val orderType: FloatingWordOrderType? = null,
    val currentWordId: Long? = null,
    val randomRemainingWordIds: List<Long> = emptyList(),
    val randomSeenWordIds: Set<Long> = emptySet()
)

internal data class FloatingWordSequenceAdvance(
    val state: FloatingWordSequenceState,
    val wordId: Long?
)

internal fun advanceFloatingWordSequence(
    state: FloatingWordSequenceState,
    snapshot: FloatingWordSourceSnapshot,
    shuffleWordIds: (List<Long>) -> List<Long> = { it.shuffled() }
): FloatingWordSequenceAdvance {
    val eligibleIds = snapshot.wordIds.asSequence().filter { it > 0L }.distinct().toList()
    if (eligibleIds.isEmpty()) {
        return FloatingWordSequenceAdvance(
            state = FloatingWordSequenceState(
                sourceKey = snapshot.sourceKey,
                orderType = snapshot.orderType
            ),
            wordId = null
        )
    }

    val sourceChanged = state.sourceKey != snapshot.sourceKey ||
        state.orderType != snapshot.orderType
    return if (snapshot.orderType == FloatingWordOrderType.RANDOM) {
        advanceRandomSequence(
            state = state,
            sourceKey = snapshot.sourceKey,
            eligibleIds = eligibleIds,
            sourceChanged = sourceChanged,
            shuffleWordIds = shuffleWordIds
        )
    } else {
        val currentWordId = state.currentWordId
        val selectedIndex = when {
            currentWordId == null -> 0
            sourceChanged -> eligibleIds.indexOfFirst { it != currentWordId }.takeIf { it >= 0 } ?: 0
            currentWordId !in eligibleIds -> 0
            else -> (eligibleIds.indexOf(currentWordId) + 1) % eligibleIds.size
        }
        val selectedWordId = eligibleIds[selectedIndex]
        FloatingWordSequenceAdvance(
            state = FloatingWordSequenceState(
                sourceKey = snapshot.sourceKey,
                orderType = snapshot.orderType,
                currentWordId = selectedWordId
            ),
            wordId = selectedWordId
        )
    }
}

private fun advanceRandomSequence(
    state: FloatingWordSequenceState,
    sourceKey: FloatingWordSourceKey,
    eligibleIds: List<Long>,
    sourceChanged: Boolean,
    shuffleWordIds: (List<Long>) -> List<Long>
): FloatingWordSequenceAdvance {
    val eligibleSet = eligibleIds.toSet()
    val previousWordId = state.currentWordId
    var remaining = if (sourceChanged) {
        emptyList()
    } else {
        state.randomRemainingWordIds.filter { it in eligibleSet && it != previousWordId }.distinct()
    }
    var seen = if (sourceChanged) {
        emptySet()
    } else {
        state.randomSeenWordIds.filterTo(linkedSetOf()) { it in eligibleSet }
    }

    if (!sourceChanged) {
        val knownIds = remaining.toSet() + seen + setOfNotNull(previousWordId)
        val addedIds = eligibleIds.filterNot(knownIds::contains)
        if (addedIds.isNotEmpty()) {
            remaining = remaining + shuffleWordIds(addedIds)
        }
    }

    if (remaining.isEmpty()) {
        val withoutImmediateRepeat = eligibleIds.filter { it != previousWordId }
        val nextCycle = withoutImmediateRepeat.ifEmpty { eligibleIds }
        remaining = shuffleWordIds(nextCycle)
        seen = emptySet()
    }

    val selectedWordId = remaining.first()
    val nextRemaining = remaining.drop(1)
    val nextSeen = (seen + selectedWordId).filterTo(linkedSetOf()) { it in eligibleSet }
    return FloatingWordSequenceAdvance(
        state = FloatingWordSequenceState(
            sourceKey = sourceKey,
            orderType = FloatingWordOrderType.RANDOM,
            currentWordId = selectedWordId,
            randomRemainingWordIds = nextRemaining,
            randomSeenWordIds = nextSeen
        ),
        wordId = selectedWordId
    )
}

internal fun FloatingWordSequenceState.matches(settings: FloatingWordSettings): Boolean {
    if (orderType != settings.orderType) return false
    return when (settings.sourceType) {
        FloatingWordSourceType.CURRENT_BOOK -> sourceKey is FloatingWordSourceKey.CurrentBook
        FloatingWordSourceType.SELF_SELECT -> {
            val expectedIds = settings.selectedWordIds.asSequence()
                .filter { it > 0L }
                .distinct()
                .toList()
            (sourceKey as? FloatingWordSourceKey.SelfSelect)?.requestedWordIds == expectedIds
        }
    }
}
