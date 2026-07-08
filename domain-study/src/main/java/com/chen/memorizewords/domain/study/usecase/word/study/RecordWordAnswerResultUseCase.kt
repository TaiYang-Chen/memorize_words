package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.model.learning.LearningEventAction
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventCommand
import com.chen.memorizewords.domain.study.usecase.learning.RecordLearningEventUseCase
import com.chen.memorizewords.domain.word.model.word.Word
import javax.inject.Inject

class RecordWordAnswerResultUseCase @Inject constructor(
    private val recordLearningEvent: RecordLearningEventUseCase,
    private val getCurrentBusinessDateUseCase: GetCurrentBusinessDateUseCase
) {
    suspend operator fun invoke(bookId: Long, word: Word, isCorrect: Boolean) {
        if (bookId <= 0L) return
        recordLearningEvent(
            RecordLearningEventCommand(
                bookId = bookId,
                word = word,
                action = LearningEventAction.ANSWER_RECORDED,
                correct = isCorrect,
                businessDate = getCurrentBusinessDateUseCase()
            )
        )
    }
}
