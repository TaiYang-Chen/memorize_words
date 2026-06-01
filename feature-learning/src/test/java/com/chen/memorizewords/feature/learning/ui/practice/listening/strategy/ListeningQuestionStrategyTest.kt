package com.chen.memorizewords.feature.learning.ui.practice.listening.strategy

import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.feature.learning.ui.practice.ListeningMeaningOptionFeedback
import com.chen.memorizewords.feature.learning.ui.practice.ListeningMeaningOptionUi
import com.chen.memorizewords.feature.learning.ui.practice.ListeningSpellingSlotFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningQuestionStrategyTest {

    @Test
    fun `meaning strategy marks correct and wrong option feedback`() {
        val strategy = MeaningListeningQuestionStrategy()
        val options = listOf(
            ListeningMeaningOptionUi(1L, "n.", "wrong", isCorrect = false),
            ListeningMeaningOptionUi(2L, "v.", "right", isCorrect = true),
            ListeningMeaningOptionUi(3L, "adj.", "wrong", isCorrect = false)
        )

        assertTrue(strategy.isCorrect(options, 1))
        assertFalse(strategy.isCorrect(options, 0))
        assertEquals(
            listOf(
                ListeningMeaningOptionFeedback.WRONG,
                ListeningMeaningOptionFeedback.CORRECT,
                ListeningMeaningOptionFeedback.DEFAULT
            ),
            strategy.feedback(options, correctIndex = 1, wrongIndex = 0)
        )
    }

    @Test
    fun `spelling strategy keeps all answer letters and restores duplicate ids independently`() {
        val strategy = SpellingListeningQuestionStrategy()
        val result = strategy.buildQuestion(
            word = word(id = 7L, text = "all"),
            shuffleSeedCounter = 0L,
            firstLetterId = 100L
        )

        assertEquals(3, result.slots.size)
        assertEquals(19, result.letterPool.size)
        assertTrue(result.letterPool.count { it.character == "l" } >= 2)

        val firstL = result.letterPool.first { it.character == "l" }
        var changed = strategy.selectLetter(result.slots, result.letterPool, firstL.id)!!
        val secondL = changed.letterPool.first { it.character == "l" && !it.isUsed }
        assertNotEquals(firstL.id, secondL.id)
        changed = strategy.selectLetter(changed.slots, changed.letterPool, secondL.id)!!

        assertTrue(changed.letterPool.first { it.id == firstL.id }.isUsed)
        assertTrue(changed.letterPool.first { it.id == secondL.id }.isUsed)

        val afterDelete = strategy.deleteLast(changed.slots, changed.letterPool)!!
        assertTrue(afterDelete.letterPool.first { it.id == firstL.id }.isUsed)
        assertFalse(afterDelete.letterPool.first { it.id == secondL.id }.isUsed)
    }

    @Test
    fun `spelling strategy detects wrong slots by normalized answer`() {
        val strategy = SpellingListeningQuestionStrategy()
        val result = strategy.buildQuestion(
            word = word(id = 8L, text = "cat"),
            shuffleSeedCounter = 1L,
            firstLetterId = 200L
        )
        val c = result.letterPool.first { it.character == "c" }
        val a = result.letterPool.first { it.character == "a" }
        val wrongLetter = result.letterPool.first { it.character !in setOf("c", "a", "t") }
        val afterC = strategy.selectLetter(result.slots, result.letterPool, c.id)!!
        val afterA = strategy.selectLetter(afterC.slots, afterC.letterPool, a.id)!!
        val afterZ = strategy.selectLetter(afterA.slots, afterA.letterPool, wrongLetter.id)!!

        val wrongSlots = strategy.wrongSlots(afterZ.slots, "cat")

        assertEquals("ca${wrongLetter.character}", strategy.input(afterZ.slots))
        assertEquals(
            listOf(2),
            strategy.wrongIndexes(wrongSlots)
        )
        assertEquals(ListeningSpellingSlotFeedback.WRONG, wrongSlots[2].feedback)
    }

    private fun word(id: Long, text: String): Word {
        return Word(
            id = id,
            word = text,
            normalizedWord = text.lowercase(),
            phoneticUS = null,
            phoneticUK = null,
            hasIrregularForms = false,
            memoryTip = null,
            mnemonicImageUrl = null,
            memoryAssociations = emptyList(),
            wordFamily = null,
            synonyms = emptyList(),
            antonyms = emptyList(),
            tags = emptyList(),
            notes = null,
            rootMemoryTip = null
        )
    }
}
