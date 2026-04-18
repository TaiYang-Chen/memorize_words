package com.chen.memorizewords.feature.onboarding

import com.chen.memorizewords.domain.model.wordbook.WordBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingWordBookCardFormatterTest {

    @Test
    fun `formatPlaceholderLearnerCount returns stable value for same book id`() {
        val first = formatPlaceholderLearnerCount(testWordBook(id = 12L, title = "CET-4"))
        val second = formatPlaceholderLearnerCount(testWordBook(id = 12L, title = "IELTS"))

        assertEquals(first, second)
    }

    @Test
    fun `formatPlaceholderLearnerCount uses wan suffix`() {
        val formatted = formatPlaceholderLearnerCount(testWordBook(id = 7L))

        assertTrue(formatted.contains("w"))
        assertTrue(formatted.endsWith(" 人在学"))
    }
}

private fun testWordBook(
    id: Long,
    title: String = "考研词汇大纲"
): WordBook = WordBook(
    id = id,
    title = title,
    category = "大学",
    imgUrl = "",
    description = "desc",
    totalWords = 5500,
    isPublic = true,
    createdByUserId = null
)
