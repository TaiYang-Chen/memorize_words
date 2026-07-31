package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceKey
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloatingWordSequenceReducerTest {

    @Test
    fun `fresh pool continues after current word instead of reusing stale single item`() {
        val key = FloatingWordSourceKey.CurrentBook(1L)
        val first = advanceFloatingWordSequence(
            state = FloatingWordSequenceState(),
            snapshot = snapshot(key, listOf(1L))
        )

        val next = advanceFloatingWordSequence(
            state = first.state,
            snapshot = snapshot(key, listOf(1L, 2L, 3L, 4L, 5L, 6L))
        )

        assertEquals(1L, first.wordId)
        assertEquals(2L, next.wordId)
    }

    @Test
    fun `removed current word falls back to first remaining ordered word`() {
        val key = FloatingWordSourceKey.CurrentBook(1L)
        val state = FloatingWordSequenceState(
            sourceKey = key,
            orderType = FloatingWordOrderType.ALPHABETIC_ASC,
            currentWordId = 10L
        )

        val result = advanceFloatingWordSequence(
            state = state,
            snapshot = snapshot(key, listOf(20L, 30L), FloatingWordOrderType.ALPHABETIC_ASC)
        )

        assertEquals(20L, result.wordId)
    }

    @Test
    fun `empty pool can recover on the next refresh`() {
        val key = FloatingWordSourceKey.CurrentBook(1L)
        val empty = advanceFloatingWordSequence(
            state = FloatingWordSequenceState(),
            snapshot = snapshot(key, emptyList())
        )
        val recovered = advanceFloatingWordSequence(
            state = empty.state,
            snapshot = snapshot(key, listOf(42L))
        )

        assertEquals(null, empty.wordId)
        assertEquals(42L, recovered.wordId)
    }

    @Test
    fun `random sequence does not repeat at a cycle boundary`() {
        val key = FloatingWordSourceKey.CurrentBook(1L)
        val source = snapshot(key, listOf(1L, 2L, 3L), FloatingWordOrderType.RANDOM)
        val first = advanceFloatingWordSequence(FloatingWordSequenceState(), source) { it }
        val second = advanceFloatingWordSequence(first.state, source) { it }
        val third = advanceFloatingWordSequence(second.state, source) { it }
        val nextCycle = advanceFloatingWordSequence(third.state, source) { it }

        assertEquals(listOf(1L, 2L, 3L, 1L), listOf(first.wordId, second.wordId, third.wordId, nextCycle.wordId))
        assertTrue(nextCycle.wordId != third.wordId)
    }

    @Test
    fun `refresh remains enabled for an empty card`() {
        assertTrue(resolveCardActionState(hasWord = false).refreshEnabled)
    }

    private fun snapshot(
        key: FloatingWordSourceKey,
        ids: List<Long>,
        orderType: FloatingWordOrderType = FloatingWordOrderType.MEMORY_CURVE
    ): FloatingWordSourceSnapshot {
        return FloatingWordSourceSnapshot(
            sourceKey = key,
            orderType = orderType,
            wordIds = ids
        )
    }
}
