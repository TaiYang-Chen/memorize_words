package com.chen.memorizewords.data.wordbook.local.room.model.words.form

import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordFormDto
import com.chen.memorizewords.domain.word.model.enums.FormType
import kotlin.test.Test
import kotlin.test.assertEquals

class WordFormMapperTest {

    @Test
    fun `maps backend enum form types without falling back to other`() {
        val dto = WordFormDto(
            id = 1L,
            type = "THIRD_SINGULAR",
            text = "honeymoons",
            formDefinition = "渡蜜月",
            targetWordId = 2L,
            wordId = 3L
        )

        val entity = dto.toEntity()
        val domain = entity.toDomain()

        assertEquals(WordFormEntity.FormType.THIRD_SINGULAR, entity.formType)
        assertEquals(FormType.THIRD_SINGULAR, domain.formType)
        assertEquals("渡蜜月", domain.formDefinition)
    }

    @Test
    fun `maps base form type`() {
        val dto = WordFormDto(
            id = 1L,
            type = "BASE_FORM",
            text = "honeymoon",
            wordId = 3L
        )

        val domain = dto.toEntity().toDomain()

        assertEquals(FormType.BASE_FORM, domain.formType)
    }
}
