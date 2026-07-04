package com.chen.memorizewords.domain.study.usecase.word.study

import com.chen.memorizewords.domain.study.model.record.CalendarDayStats
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.model.record.DailyStudySummary
import com.chen.memorizewords.domain.study.model.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.model.record.DayCheckInDetail
import com.chen.memorizewords.domain.study.model.record.TodayCheckInEntryState
import com.chen.memorizewords.domain.study.repository.record.LearningRecordRepository
import com.chen.memorizewords.domain.word.model.enums.PartOfSpeech
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.domain.word.model.word.WordForm
import com.chen.memorizewords.domain.word.model.word.WordQuickLookupResult
import com.chen.memorizewords.domain.word.model.word.WordRoot
import com.chen.memorizewords.domain.word.repository.WordRepository
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking

class SetWordAsMasteredUseCaseTest {

    @Test
    fun `records mastered review word as review`() = runBlocking {
        val word = testWord()
        val learningRecordRepository = FakeLearningRecordRepository()
        val wordBookRepository = FakeWordBookRepository()
        val useCase = SetWordAsMasteredUseCase(
            wordRepository = FakeWordRepository(
                definitions = listOf(
                    WordDefinitions(
                        id = 1L,
                        wordId = word.id,
                        partOfSpeech = PartOfSpeech.NOUN,
                        meaningChinese = "test meaning"
                    )
                )
            ),
            learningRecordRepository = learningRecordRepository,
            wordBookRepository = wordBookRepository,
            getCurrentBusinessDateUseCase = GetCurrentBusinessDateUseCase(learningRecordRepository)
        )

        useCase(bookId = 10L, word = word, isNewWord = false)

        assertEquals(word, learningRecordRepository.recordedWord)
        assertEquals("NOUN test meaning", learningRecordRepository.recordedDefinition)
        assertFalse(learningRecordRepository.recordedIsNewWord!!)
        assertEquals(10L, wordBookRepository.updatedBookId)
        assertEquals("2026-06-23", wordBookRepository.updatedDate)
    }

    private class FakeWordRepository(
        private val definitions: List<WordDefinitions>
    ) : WordRepository {
        override suspend fun getWordsByIds(ids: List<Long>): List<Word> = emptyList()
        override suspend fun getWordById(wordId: Long): Word? = null
        override suspend fun getWordForms(wordId: Long): List<WordForm> = emptyList()
        override suspend fun getRootWordByWordId(wordId: Long): List<WordRoot> = emptyList()
        override suspend fun getWordExamples(wordId: Long): List<WordExample> = emptyList()
        override suspend fun getWordDefinitions(wordId: Long): List<WordDefinitions> = definitions
        override suspend fun getRandomDefinition(wordId: Long): WordDefinitions = definitions.first()
        override suspend fun getRandomDefinitionsByPos(wordId: Long, limit: Int): List<WordDefinitions> = definitions
        override suspend fun updateWordStatus(bookId: Long, word: Word, quality: Int): Boolean = true
        override suspend fun setWordAsMastered(bookId: Long, word: Word) = Unit
        override suspend fun getWordByWordString(word: String): Word? = null
        override suspend fun lookupWordQuick(normalizedWord: String, rawWord: String): WordQuickLookupResult {
            error("Not needed")
        }
    }

    private class FakeLearningRecordRepository : LearningRecordRepository {
        var recordedWord: Word? = null
        var recordedDefinition: String? = null
        var recordedIsNewWord: Boolean? = null

        override fun getCurrentBusinessDate(): String = "2026-06-23"

        override suspend fun addLearningRecord(word: Word, definition: String, isNewWord: Boolean) {
            recordedWord = word
            recordedDefinition = definition
            recordedIsNewWord = isNewWord
        }

        override suspend fun addStudyDuration(durationMs: Long) = Unit
        override fun getStudyTotalDayCount(): Flow<Int> = emptyFlow()
        override fun getContinuousCheckInDays(): Flow<Int> = emptyFlow()
        override fun getTodayNewWordCount(): Flow<Int> = emptyFlow()
        override fun getTodayReviewWordCount(): Flow<Int> = emptyFlow()
        override fun getTodayStudyDurationMs(): Flow<Long> = emptyFlow()
        override fun getStudyTotalDurationMs(): Flow<Long> = emptyFlow()
        override fun getDailyWordStats(startDate: String, endDate: String): Flow<List<DailyWordStats>> = emptyFlow()
        override fun getDailyDurationStats(startDate: String, endDate: String): Flow<List<DailyDurationStats>> = emptyFlow()
        override fun getCalendarDayStats(startDate: String, endDate: String): Flow<List<CalendarDayStats>> = emptyFlow()
        override fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecord>> = emptyFlow()
        override fun getDailyStudySummary(date: String): Flow<DailyStudySummary> = emptyFlow()
        override fun getDayCheckInDetail(date: String): Flow<DayCheckInDetail> = emptyFlow()
        override suspend fun getTodayCheckInEntryState(): TodayCheckInEntryState = error("Not needed")
        override suspend fun makeUpCheckIn(date: String): Result<CheckInRecord> = error("Not needed")
        override suspend fun autoCheckInTodayIfEligible(): Result<CheckInRecord?> = error("Not needed")
    }

    private class FakeWordBookRepository : WordBookRepository {
        var updatedBookId: Long? = null
        var updatedDate: String? = null

        override fun getMyWordBooksMinimalFlow(): Flow<List<WordBookInfo>> = emptyFlow()
        override fun getCurrentWordBookMinimalFlow(): Flow<WordBookInfo?> = emptyFlow()
        override suspend fun setCurrentWordBook(bookId: Long) = Unit
        override suspend fun deleteMyWordBook(bookId: Long): Result<Unit> = error("Not needed")
        override suspend fun getCurrentWordBook(): WordBook? = null
        override suspend fun getBookNameById(bookId: Long): String? = null
        override suspend fun getWordListSummary(
            wordBookId: Long,
            now: Long
        ): com.chen.memorizewords.domain.wordbook.model.WordListSummary = com.chen.memorizewords.domain.wordbook.model.WordListSummary()
        override suspend fun getWordRowsPage(query: WordListQuery) = error("Not needed")
        override suspend fun getWordRowIds(query: WordListQuery, limit: Int): List<Long> = emptyList()
        override suspend fun getWordIdsPage(wordBookId: Long, pageIndex: Int, pageSize: Int): List<Long> = emptyList()
        override suspend fun getAllUnlearnedWordsForBook(bookId: Long): List<Word> = emptyList()
        override suspend fun getUnlearnedWordIdsForBook(
            bookId: Long,
            count: Int,
            orderType: WordOrderType,
            excludeIds: Set<Long>
        ): List<Long> = emptyList()
        override suspend fun updateBookStudyDay(bookId: Long, today: String) {
            updatedBookId = bookId
            updatedDate = today
        }
        override suspend fun recordAnswerResult(bookId: Long, isCorrect: Boolean, today: String) = Unit
    }
}

private fun testWord(): Word {
    return Word(
        id = 100L,
        word = "test",
        normalizedWord = "test",
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
