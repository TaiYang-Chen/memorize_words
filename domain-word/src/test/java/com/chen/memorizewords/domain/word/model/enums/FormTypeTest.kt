package com.chen.memorizewords.domain.word.model.enums

import kotlin.test.Test
import kotlin.test.assertEquals

class FormTypeTest {

    @Test
    fun `provides Chinese display names for common word forms`() {
        assertEquals("现在分词", FormType.PRESENT_PARTICIPLE.displayName)
        assertEquals("过去分词", FormType.PAST_PARTICIPLE.displayName)
        assertEquals("复数", FormType.PLURAL.displayName)
        assertEquals("第三人称单数", FormType.THIRD_SINGULAR.displayName)
        assertEquals("过去式", FormType.PAST_TENSE.displayName)
        assertEquals("其他形式", FormType.OTHER.displayName)
    }
}
