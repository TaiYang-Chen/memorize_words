package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.model.learning.LearningEventAction
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventCommand
import com.chen.memorizewords.domain.study.usecase.learning.RecordLearningEventUseCase
import com.chen.memorizewords.domain.word.model.word.Word
import javax.inject.Inject

class SetWordAsMasteredUseCase @Inject constructor(
    private val recordLearningEvent: RecordLearningEventUseCase,
    private val getCurrentBusinessDateUseCase: GetCurrentBusinessDateUseCase
) {
    suspend operator fun invoke(
        bookId: Long,
        word: Word,
        isNewWord: Boolean = true
    ) {
        recordLearningEvent(
            RecordLearningEventCommand(
                bookId = bookId,
                word = word,
                action = LearningEventAction.MASTERED,
                quality = 5,
                isNewWordOverride = isNewWord,
                businessDate = getCurrentBusinessDateUseCase(),
                payloadJson = """{"isNewWord":$isNewWord}"""
            )
        )
    }
}
