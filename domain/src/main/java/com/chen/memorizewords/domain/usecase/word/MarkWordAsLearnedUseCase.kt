package com.chen.memorizewords.domain.usecase.word

import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.repository.WordBookRepository
import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import com.chen.memorizewords.domain.repository.word.WordRepository
import com.chen.memorizewords.domain.usecase.word.study.GetCurrentBusinessDateUseCase
import javax.inject.Inject

class MarkWordAsLearnedUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val learningRecordRepository: LearningRecordRepository,
    private val wordBookRepository: WordBookRepository,
    private val getCurrentBusinessDateUseCase: GetCurrentBusinessDateUseCase
) {
    suspend operator fun invoke(
        bookId: Long,
        word: Word,
        quality: Int
    ) {
        val isNewWord = wordRepository.updateWordStatus(bookId, word, quality)
        val definitions = wordRepository.getWordDefinitions(word.id)
        val definition = definitions.joinToString("; ") {
            "${it.partOfSpeech} ${it.meaningChinese}"
        }
        learningRecordRepository.addLearningRecord(word, definition, isNewWord)
        val today = getCurrentBusinessDateUseCase()
        wordBookRepository.updateBookStudyDay(bookId, today)
    }
}
