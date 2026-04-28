package com.chen.memorizewords.feature.wordbook.shop

import org.junit.Assert.assertEquals
import org.junit.Test

class BookShopWordBookCardFormatterTest {

    @Test
    fun `word count uses badge style text`() {
        assertEquals("1,234 \u8bcd", formatBookShopWordCount(1234))
    }

    @Test
    fun `meta combines category and visibility`() {
        assertEquals(
            "\u5927\u5b66 \u00b7 \u516c\u5f00",
            formatBookShopMeta("\u5927\u5b66", "\u516c\u5f00")
        )
    }

    @Test
    fun `meta falls back to visibility when category is blank`() {
        assertEquals(
            "\u79c1\u6709",
            formatBookShopMeta("   ", "\u79c1\u6709")
        )
    }
}
