package com.chen.memorizewords.data.wordbook.repository.floating

import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import kotlin.test.Test
import kotlin.test.assertEquals

class FloatingWordSourceRepositoryPolicyTest {

    @Test
    fun `source ids discard invalid and duplicate selections while preserving order`() {
        assertEquals(
            listOf(3L, 8L, 1L),
            normalizeFloatingWordSourceIds(listOf(3L, -1L, 8L, 3L, 0L, 1L))
        )
    }

    @Test
    fun `source ordering supports alphabetic length and memory curve`() {
        val ids = listOf(1L, 2L, 3L)
        val words = mapOf(
            1L to FloatingWordSortValue("long", "long"),
            2L to FloatingWordSortValue("a", "a"),
            3L to FloatingWordSortValue("be", "be")
        )
        val learning = mapOf(
            1L to FloatingWordLearningSortValue(30L, 3L),
            2L to FloatingWordLearningSortValue(10L, 2L),
            3L to FloatingWordLearningSortValue(20L, 1L)
        )

        assertEquals(
            listOf(2L, 3L, 1L),
            orderFloatingWordSourceIds(ids, FloatingWordOrderType.ALPHABETIC_ASC, words)
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            orderFloatingWordSourceIds(ids, FloatingWordOrderType.LENGTH_ASC, words)
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            orderFloatingWordSourceIds(ids, FloatingWordOrderType.MEMORY_CURVE, words, learning)
        )
    }

    @Test
    fun `missing word rows are excluded from a source snapshot`() {
        val words = mapOf(2L to FloatingWordSortValue("present", "present"))

        assertEquals(
            listOf(2L),
            orderFloatingWordSourceIds(
                wordIds = listOf(1L, 2L),
                orderType = FloatingWordOrderType.RANDOM,
                wordsById = words
            )
        )
    }
}
