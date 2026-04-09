package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.repository.WordBookRepository
import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import com.chen.memorizewords.domain.repository.word.WordRepository
import javax.inject.Inject

class SetWordAsMasteredUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val learningRecordRepository: LearningRecordRepository,
    private val wordBookRepository: WordBookRepository,
    private val getCurrentBusinessDateUseCase: GetCurrentBusinessDateUseCase
) {
    suspend operator fun invoke(
        bookId: Long,
        word: Word,
    ) {
        wordRepository.setWordAsMastered(bookId, word)
        val definitions = wordRepository.getWordDefinitions(word.id)
        val definition = definitions.joinToString("; ") {
            "${it.partOfSpeech} ${it.meaningChinese}"
        }
        learningRecordRepository.addLearningRecord(
            word,
            definition,
            true
        )
        val today = getCurrentBusinessDateUseCase()
        wordBookRepository.updateBookStudyDay(bookId, today)
    }
}
