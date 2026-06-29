package com.chen.memorizewords.data.wordbook.repository.wordbook

import com.chen.memorizewords.data.wordbook.local.room.model.words.example.WordExampleEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.form.WordFormEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordBookPagePersisterTest {

    @Test
    fun `form keeps target word id when target exists in page or local database`() {
        val forms = listOf(
            form(id = 1L, wordId = 10L, formWordId = 11L),
            form(id = 2L, wordId = 10L, formWordId = 99L)
        )

        val sanitized = sanitizeWordForms(
            forms = forms,
            validWordIds = setOf(10L, 11L, 99L)
        )

        assertEquals(11L, sanitized[0].formWordId)
        assertEquals(99L, sanitized[1].formWordId)
        assertEquals("form definition 1", sanitized[0].formDefinition)
    }

    @Test
    fun `form drops missing target word id`() {
        val sanitized = sanitizeWordForms(
            forms = listOf(form(id = 1L, wordId = 10L, formWordId = 99L)),
            validWordIds = setOf(10L)
        )

        assertNull(sanitized.single().formWordId)
    }

    @Test
    fun `form keeps current word id`() {
        val sanitized = sanitizeWordForms(
            forms = listOf(form(id = 1L, wordId = 10L, formWordId = 10L)),
            validWordIds = setOf(10L)
        )

        assertEquals(10L, sanitized.single().formWordId)
    }

    @Test
    fun `lookup style form drops remote target word id when target is not local`() {
        val sanitized = sanitizeWordForms(
            forms = listOf(
                form(id = 11174L, wordId = 1084804L, formWordId = 788481L),
                form(id = 147652L, wordId = 1084804L, formWordId = 1084253L)
            ),
            validWordIds = setOf(1084804L)
        )

        assertNull(sanitized[0].formWordId)
        assertNull(sanitized[1].formWordId)
    }

    @Test
    fun `example keeps definition id when definition belongs to current word`() {
        val sanitized = sanitizeWordExamples(
            examples = listOf(example(id = 1L, wordId = 10L, definitionId = 100L)),
            validDefinitionIds = setOf(100L)
        )

        assertEquals(100L, sanitized.single().definitionId)
    }

    @Test
    fun `example drops missing definition id`() {
        val sanitized = sanitizeWordExamples(
            examples = listOf(example(id = 1L, wordId = 10L, definitionId = 404L)),
            validDefinitionIds = setOf(100L)
        )

        assertNull(sanitized.single().definitionId)
    }

    private fun form(
        id: Long,
        wordId: Long,
        formWordId: Long?
    ): WordFormEntity {
        return WordFormEntity(
            id = id,
            wordId = wordId,
            formWordId = formWordId,
            formType = WordFormEntity.FormType.OTHER,
            formText = "form-$id",
            formDefinition = "form definition $id"
        )
    }

    private fun example(
        id: Long,
        wordId: Long,
        definitionId: Long?
    ): WordExampleEntity {
        return WordExampleEntity(
            id = id,
            wordId = wordId,
            definitionId = definitionId,
            englishSentence = "example $id"
        )
    }
}
