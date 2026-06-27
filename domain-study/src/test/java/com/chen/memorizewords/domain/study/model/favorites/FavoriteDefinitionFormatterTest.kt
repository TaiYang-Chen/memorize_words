package com.chen.memorizewords.domain.study.model.favorites

import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteDefinitionFormatterTest {

    @Test
    fun `converts enum part of speech names to abbreviations`() {
        val formatted = FavoriteDefinitionFormatter.abbreviatePartsOfSpeech(
            "VERB 生产 NOUN 制成品"
        )

        assertEquals("v. 生产 n. 制成品", formatted)
    }

    @Test
    fun `keeps existing abbreviations as abbreviations`() {
        val formatted = FavoriteDefinitionFormatter.abbreviatePartsOfSpeech(
            "v. 生产 adj. 平稳的"
        )

        assertEquals("v. 生产 adj. 平稳的", formatted)
    }
}
