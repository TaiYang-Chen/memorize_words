package com.chen.memorizewords.domain.study.usecase.word
import com.chen.memorizewords.domain.study.model.learning.LearningEventAction
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventCommand
import com.chen.memorizewords.domain.study.usecase.learning.RecordLearningEventUseCase
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.study.usecase.word.study.GetCurrentBusinessDateUseCase
import javax.inject.Inject

class MarkWordAsLearnedUseCase @Inject constructor(
    private val recordLearningEvent: RecordLearningEventUseCase,
    private val getCurrentBusinessDateUseCase: GetCurrentBusinessDateUseCase
) {
    suspend operator fun invoke(
        bookId: Long,
        word: Word,
        quality: Int
    ) {
        recordLearningEvent(
            RecordLearningEventCommand(
                bookId = bookId,
                word = word,
                action = LearningEventAction.LEARNED,
                quality = quality,
                businessDate = getCurrentBusinessDateUseCase()
            )
        )
    }
}
