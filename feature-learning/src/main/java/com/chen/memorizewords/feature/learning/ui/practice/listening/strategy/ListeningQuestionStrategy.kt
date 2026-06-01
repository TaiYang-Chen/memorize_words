package com.chen.memorizewords.feature.learning.ui.practice.listening.strategy

import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.practice.policy.SpellingAnswerPolicy
import com.chen.memorizewords.feature.learning.ui.practice.ListeningMeaningOptionFeedback
import com.chen.memorizewords.feature.learning.ui.practice.ListeningMeaningOptionUi
import com.chen.memorizewords.feature.learning.ui.practice.ListeningQuestionType
import com.chen.memorizewords.feature.learning.ui.practice.ListeningSpellingLetterUi
import com.chen.memorizewords.feature.learning.ui.practice.ListeningSpellingSlotFeedback
import com.chen.memorizewords.feature.learning.ui.practice.ListeningSpellingSlotUi
import kotlin.random.Random

internal interface ListeningQuestionStrategy {
    val questionType: ListeningQuestionType
}

internal class MeaningListeningQuestionStrategy : ListeningQuestionStrategy {
    override val questionType: ListeningQuestionType = ListeningQuestionType.MEANING

    fun isCorrect(options: List<ListeningMeaningOptionUi>, selectedIndex: Int): Boolean {
        return options.getOrNull(selectedIndex)?.isCorrect == true
    }

    fun defaultFeedback(options: List<ListeningMeaningOptionUi>): List<ListeningMeaningOptionFeedback> {
        return List(options.size) { ListeningMeaningOptionFeedback.DEFAULT }
    }

    fun feedback(
        options: List<ListeningMeaningOptionUi>,
        correctIndex: Int? = null,
        wrongIndex: Int? = null
    ): List<ListeningMeaningOptionFeedback> {
        return options.indices.map { index ->
            when (index) {
                wrongIndex -> ListeningMeaningOptionFeedback.WRONG
                correctIndex -> ListeningMeaningOptionFeedback.CORRECT
                else -> ListeningMeaningOptionFeedback.DEFAULT
            }
        }
    }
}

internal data class SpellingQuestionBuildResult(
    val slots: List<ListeningSpellingSlotUi>,
    val letterPool: List<ListeningSpellingLetterUi>,
    val nextLetterId: Long
)

internal class SpellingListeningQuestionStrategy : ListeningQuestionStrategy {
    override val questionType: ListeningQuestionType = ListeningQuestionType.SPELLING
    private val spellingAnswerPolicy = SpellingAnswerPolicy()

    fun buildQuestion(
        word: Word,
        shuffleSeedCounter: Long,
        firstLetterId: Long
    ): SpellingQuestionBuildResult {
        val answerCharacters = answerCharacters(word.word).map(Char::toString)
        val random = Random(word.id * SEED_MULTIPLIER + shuffleSeedCounter)
        val poolCharacters = answerCharacters.toMutableList()
        val targetPoolSize = maxOf(POOL_TARGET_COUNT, answerCharacters.size)
        repeat(targetPoolSize - answerCharacters.size) {
            poolCharacters += DISTRACTOR_ALPHABET.random(random).toString()
        }
        var nextLetterId = firstLetterId
        val letterPool = poolCharacters
            .shuffled(random)
            .map { character ->
                ListeningSpellingLetterUi(
                    id = nextLetterId++,
                    character = character
                )
            }
        return SpellingQuestionBuildResult(
            slots = List(answerCharacters.size) { ListeningSpellingSlotUi() },
            letterPool = letterPool,
            nextLetterId = nextLetterId
        )
    }

    fun selectLetter(
        slots: List<ListeningSpellingSlotUi>,
        letterPool: List<ListeningSpellingLetterUi>,
        letterId: Long
    ): SpellingDraftChange? {
        val nextSlotIndex = slots.indexOfFirst { it.sourceLetterId == null }
        if (nextSlotIndex < 0) return null
        val selectedIndex = letterPool.indexOfFirst { it.id == letterId && !it.isUsed }
        if (selectedIndex < 0) return null
        val selectedLetter = letterPool[selectedIndex]
        val updatedSlots = slots.toMutableList().apply {
            this[nextSlotIndex] = ListeningSpellingSlotUi(
                character = selectedLetter.character,
                sourceLetterId = selectedLetter.id
            )
        }
        val updatedLetterPool = letterPool.toMutableList().apply {
            this[selectedIndex] = selectedLetter.copy(isUsed = true)
        }
        return SpellingDraftChange(updatedSlots, updatedLetterPool)
    }

    fun deleteLast(
        slots: List<ListeningSpellingSlotUi>,
        letterPool: List<ListeningSpellingLetterUi>
    ): SpellingDraftChange? {
        val lastFilledIndex = slots.indexOfLast { it.sourceLetterId != null }
        if (lastFilledIndex < 0) return null
        val releasedLetterId = slots[lastFilledIndex].sourceLetterId
        val updatedSlots = slots.toMutableList().apply {
            this[lastFilledIndex] = ListeningSpellingSlotUi()
        }
        val updatedLetterPool = letterPool.map { letter ->
            if (letter.id == releasedLetterId) {
                letter.copy(isUsed = false)
            } else {
                letter
            }
        }
        return SpellingDraftChange(updatedSlots, updatedLetterPool)
    }

    fun resetFeedback(slots: List<ListeningSpellingSlotUi>): List<ListeningSpellingSlotUi> {
        return slots.map { it.copy(feedback = ListeningSpellingSlotFeedback.DEFAULT) }
    }

    fun input(slots: List<ListeningSpellingSlotUi>): String {
        return slots.joinToString(separator = "") { it.character }
    }

    fun isCorrect(input: String, answer: String): Boolean {
        return spellingAnswerPolicy.isCorrectIgnoringWhitespace(input, answer)
    }

    fun wrongSlots(
        slots: List<ListeningSpellingSlotUi>,
        answerWord: String
    ): List<ListeningSpellingSlotUi> {
        val answerCharacters = answerCharacters(answerWord).map { it.lowercaseChar().toString() }
        return slots.mapIndexed { index, slot ->
            val expected = answerCharacters.getOrNull(index)
            val actual = slot.character.trim().lowercase()
            slot.copy(
                feedback = if (expected != null && actual == expected) {
                    ListeningSpellingSlotFeedback.DEFAULT
                } else {
                    ListeningSpellingSlotFeedback.WRONG
                }
            )
        }
    }

    fun wrongIndexes(slots: List<ListeningSpellingSlotUi>): List<Int> {
        return slots.mapIndexedNotNull { index, slot ->
            index.takeIf { slot.feedback == ListeningSpellingSlotFeedback.WRONG }
        }
    }

    private fun answerCharacters(answer: String): List<Char> {
        return answer.filterNot(Char::isWhitespace).toList()
    }

    companion object {
        private const val POOL_TARGET_COUNT = 19
        private const val DISTRACTOR_ALPHABET = "abcdefghijklmnopqrstuvwxyz"
        private const val SEED_MULTIPLIER = 37
    }
}

internal data class SpellingDraftChange(
    val slots: List<ListeningSpellingSlotUi>,
    val letterPool: List<ListeningSpellingLetterUi>
)
