package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.repository.LearningProgressRepository
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.let
import kotlin.math.roundToInt

class GetCurrentWordBookInfoFlowUseCase @Inject constructor(
    private val wordBookRepo: WordBookRepository,
    private val progressRepo: LearningProgressRepository
) {
    /**
     * 返回一个 Flow，当以下任意数据变化时自动刷新：
     * - 当前词书信息发生变化
     * - 当前词书的学习进度发生变化
     */
    operator fun invoke(): Flow<WordBookInfo?> {
        val currentBookFlow: Flow<WordBookInfo?> =
            wordBookRepo.getCurrentWordBookMinimalFlow()
                .distinctUntilChanged()

        val progressFlow =
            currentBookFlow
                .map { it?.bookId }
                .distinctUntilChanged()
                .flatMapLatest { bookId ->
                    if (bookId == null || bookId < 0) {
                        flowOf(null)
                    } else {
                        progressRepo.getProgressByWordBookId(bookId)
                    }
                }

        return combine(currentBookFlow, progressFlow) { book, stats ->
            book?.let {
                it.copy(
                    learningWords = stats?.learningCount ?: 0,
                    masteredWords = stats?.masteredCount ?: 0,
                    studyDayCount = stats?.studyDayCount ?: 0,
                    accuracyRate = calculateAccuracyRate(
                        stats?.correctCount ?: 0,
                        stats?.wrongCount ?: 0
                    )
                )
            }
        }
    }

    private fun calculateAccuracyRate(correctCount: Int, wrongCount: Int): Float {
        val total = correctCount + wrongCount
        if (total <= 0) return 0f
        val rate = correctCount * 100f / total.toFloat()
        return (rate * 10f).roundToInt() / 10f
    }
}
